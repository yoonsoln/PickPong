package com.example.pickpongfine;
//package com.robotemi.sdk.listeners;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.util.UnstableApi; // from RoboTemiListeners

import com.robotemi.sdk.NlpResult;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.activitystream.ActivityStreamPublishMessage;
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener;
import com.robotemi.sdk.listeners.OnConstraintBeWithStatusChangedListener;
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;
import com.robotemi.sdk.listeners.OnLocationsUpdatedListener;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

import org.json.JSONException; // JSON 처리를 위해 추가
import org.json.JSONObject;   // JSON 처리를 위해 추가

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import android.view.View.OnClickListener;
import android.widget.Button;
import android.os.Handler;
import android.os.Looper;

import android.widget.TextView;
import android.os.SystemClock; // SystemClock.elapsedRealtime() 사용을 위해 추가


// MainActivity가 직접 Robot의 리스너들을 구현
public class MainActivity extends AppCompatActivity implements
        Robot.NlpListener,
        Robot.ConversationViewAttachesListener,
        Robot.WakeupWordListener,
        Robot.ActivityStreamPublishListener,
        Robot.TtsListener,
        OnBeWithMeStatusChangedListener,
        OnGoToLocationStatusChangedListener,
        OnLocationsUpdatedListener,
        OnConstraintBeWithStatusChangedListener,
        OnDetectionStateChangedListener,
        OnRobotReadyListener,
        Robot.AsrListener {

    private Robot robot;

    private static final String TAG = "MainActivity";
    private static final String BASE_URL = "http://192.168.0.241:3000";

    // RoboTemiListeners listeners = new RoboTemiListeners();
    private View btnPick;
    private View btnPong;
    private TextView tvBallPosition;

    // 주기적인 작업을 위한 Handler 및 Runnable
    private Handler movementHandler;
    private Runnable movementRunnable;
    private final long MOVEMENT_INTERVAL_MS = 100; // 500ms 간격 -> 100ms로 변경

    // 직진 상태 유지를 위한 변수 추가
    private long lastCenterDetectionTimeMillis = 0; // 마지막으로 공이 중앙에 감지된 시간
    private static final long STRAIGHT_DURATION_MS = 3000; // 직진 상태를 유지할 시간 (3초)
    private boolean isForcedStraightMode = false; // 강제 직진 모드 플래그


    public interface ApiResponseListener {
        void onSuccess(String response);
        void onError(String errorMessage);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        btnPick = findViewById(R.id.btnPick);
        btnPong = findViewById(R.id.btnPong);
        tvBallPosition = findViewById(R.id.tvBallPosition); // 초기화

        robot = Robot.getInstance(); // Robot 인스턴스 가져오기

        // Handler 초기화
        movementHandler = new Handler(Looper.getMainLooper());
        movementRunnable = new Runnable() {
            @Override
            public void run() {
                // 로봇이 준비되었고, 액티비티가 활성 상태일 때만 실행
                if (robot != null && robot.isReady() && !isFinishing() && !isDestroyed()) {
                    getBallPositionData(); // 공 위치를 가져와서 로봇 제어
                    movementHandler.postDelayed(this, MOVEMENT_INTERVAL_MS); // 다음 실행 예약
                } else {
                    Log.w(TAG, "Movement runnable stopped: Robot not ready or activity finishing.");
                }
            }
        };

        if (btnPick == null || btnPong == null) {
            Log.e("MainActivity", "Button not found in layout");
            // return; // 여기서 리턴하면 리스너 등록 등이 실행되지 않으므로 주의
        }

        if (btnPick != null) {
            btnPick.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    // Pick();

                    Log.d(TAG, "Pick button clicked. Starting detection and periodic movement.");
                    // 1. /start_detection POST 요청 (기존과 동일)

                    sendPostRequest("/start_detection", new ApiResponseListener() {
                        @Override
                        public void onSuccess(String response) {
                            Log.i(TAG, "/start_detection Success: " + response);
                            // UI 업데이트는 메인 스레드에서 수행해야 합니다.
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        JSONObject jsonResponse = new JSONObject(response);
                                        String status = jsonResponse.getString("status");
                                        Toast.makeText(MainActivity.this, "탐지 시작: " + status, Toast.LENGTH_SHORT).show();

                                        // 2. /start_detection 성공 후, 주기적인 공 위치 확인 및 이동 시작
                                        startPeriodicMovement(); // <--- 이 부분 수정

                                        // getBallPositionData(); // 새로운 메서드 호출

                                    } catch (JSONException e) {
                                        Log.e(TAG, "JSON parsing error for /start_detection: ", e);
                                        Toast.makeText(MainActivity.this, "탐지 시작 응답 파싱 오류", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "/start_detection Error: " + errorMessage);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "탐지 시작 실패: " + errorMessage, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });

                }

            });
        }


        if (btnPong != null) {
            btnPong.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, "Pong button clicked. Stopping detection and movement.");
                    stopPeriodicMovement(); // 주기적인 움직임 중단
                    Pong(); // 로봇 동작

                    // 강제 직진 모드 해제
                    isForcedStraightMode = false;
                    lastCenterDetectionTimeMillis = 0;

                    // 서버로 /stop_detection POST 요청 보내기
                    sendPostRequest("/stop_detection", new ApiResponseListener() {
                        @Override
                        public void onSuccess(String response) {
                            Log.i(TAG, "/stop_detection Success: " + response);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        JSONObject jsonResponse = new JSONObject(response);
                                        String status = jsonResponse.getString("status");
                                        Toast.makeText(MainActivity.this, "탐지 중지: " + status, Toast.LENGTH_SHORT).show();
                                    } catch (JSONException e) {
                                        Log.e(TAG, "JSON parsing error for /stop_detection: ", e);
                                        Toast.makeText(MainActivity.this, "중지 응답 파싱 오류", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "/stop_detection Error: " + errorMessage);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "탐지 중지 실패: " + errorMessage, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                }
            });
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    // 주기적인 움직임 시작 메서드
    private void startPeriodicMovement() {
        Log.d(TAG, "Starting periodic movement updates.");
        isForcedStraightMode = false; // 주기적 움직임 시작 시 강제 직진 모드 초기화
        lastCenterDetectionTimeMillis = 0;

        // 이미 실행 중인 Runnable이 있다면 제거 후 새로 시작 (중복 실행 방지)
        movementHandler.removeCallbacks(movementRunnable);
        movementHandler.post(movementRunnable); // 즉시 첫 실행 후, Runnable 내부에서 다음 실행 예약
    }

    // 주기적인 움직임 중단 메서드

    private void stopPeriodicMovement() {
        Log.d(TAG, "Stopping periodic movement updates.");
        if (movementHandler != null && movementRunnable != null) { // null 체크 추가
            movementHandler.removeCallbacks(movementRunnable);
        }
        isForcedStraightMode = false; // 주기적 움직임 중단 시 강제 직진 모드 해제
        lastCenterDetectionTimeMillis = 0;

        // 필요하다면 즉시 로봇 움직임 중지
        if (robot != null && robot.isReady()) {
            robot.stopMovement(); // 현재 진행 중인 모든 움직임(skidJoy, turnBy 등)을 중지
            Log.d(TAG, "Robot movement explicitly stopped in stopPeriodicMovement.");
        }
    }

    /*
    private void stopPeriodicMovement() {

        Log.d(TAG, "Stopping periodic movement updates.");
        movementHandler.removeCallbacks(movementRunnable);
        // 필요하다면 즉시 로봇 움직임 중지
        if (robot != null && robot.isReady()) {
            robot.stopMovement(); // Pong()에서도 호출되지만, 여기서도 명시적으로 호출 가능
        }
    }*/

    // 메서드 이름을 sendPostRequest로, 콜백을 받도록 설정
    private void sendPostRequest(final String path, final ApiResponseListener listener) {
        // 네트워크 작업은 별도 스레드에서 수행
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                BufferedReader reader = null;
                String responseJsonString = null;
                String errorMessage = null;

                try {
                    URL url = new URL(BASE_URL + path);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(3000); // 연결 타임아웃 3초
                    conn.setReadTimeout(3000);    // 읽기 타임아웃 3초
                    conn.setDoOutput(true);       // POST 데이터를 보내기 위함
                    conn.setDoInput(true);        // 응답을 받기 위함
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8"); // 요청 형식

                    // 서버에서 빈 본문을 예상하므로, 빈 바이트 배열을 보냅니다.
                    OutputStream os = conn.getOutputStream();
                    // 서버는 빈 본문을 예상하므로, 여기서는 아무것도 쓰지 않거나 빈 바이트 배열을 보냅니다.
                    // os.write(new byte[0]); // 명시적으로 빈 바이트를 보내고 싶다면 이 줄의 주석을 해제합니다.
                    os.flush(); // 버퍼 비우기
                    os.close(); // 스트림 닫기

                    int responseCode = conn.getResponseCode();
                    Log.d(TAG, path + " Response Code: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = conn.getInputStream();
                        StringBuilder buffer = new StringBuilder();
                        if (inputStream == null) {
                            errorMessage = "No input stream from server.";
                            Log.w(TAG, errorMessage + " for path: " + path);
                        } else {
                            reader = new BufferedReader(new InputStreamReader(inputStream));
                            String line;
                            while ((line = reader.readLine()) != null) {
                                buffer.append(line); // 개행 문자(\n)는 JSON 응답에서 보통 불필요
                            }
                            if (buffer.length() == 0) {
                                // HTTP OK를 받았지만 응답 본문이 비어있는 경우. 서버의 응답 형식에 따라 정상일 수도, 에러일 수도 있습니다.
                                // 현재 Node.js 서버는 {"status":"..."} 형태의 JSON을 반환하므로, 비어있다면 문제일 수 있습니다.
                                Log.w(TAG, "Empty response from server for path: " + path + " (HTTP_OK)");
                                // 이 경우를 에러로 처리할지, 또는 빈 문자열을 성공으로 전달할지 결정해야 합니다.
                                // 여기서는 빈 문자열을 성공으로 전달하고, 호출부에서 JSON 파싱 시 에러가 발생하도록 합니다.
                                responseJsonString = "";
                            } else {
                                responseJsonString = buffer.toString();
                            }
                        }
                    } else {
                        errorMessage = "Server returned non-OK status: " + responseCode;
                        Log.e(TAG, errorMessage + " for path: " + path);
                        // 에러 스트림이 있다면 읽어서 로깅 (더 자세한 오류 파악에 도움)
                        InputStream errorStream = conn.getErrorStream();
                        if (errorStream != null) {
                            BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
                            StringBuilder errorBuffer = new StringBuilder();
                            String errorLine;
                            while ((errorLine = errorReader.readLine()) != null) {
                                errorBuffer.append(errorLine);
                            }
                            Log.e(TAG, "Error stream content for " + path + ": " + errorBuffer.toString());
                            // 실제 에러 메시지에 이 내용을 포함할 수도 있습니다.
                            errorMessage += " - Details: " + errorBuffer.toString();
                            errorReader.close();
                        }
                    }
                } catch (java.net.SocketTimeoutException e) {
                    errorMessage = "Timeout: " + e.getMessage();
                    Log.e(TAG, "POST " + path + " failed with SocketTimeoutException", e);
                } catch (java.net.ConnectException e) {
                    errorMessage = "Connection failed: " + e.getMessage() + ". Check server IP and port, and network connection.";
                    Log.e(TAG, "POST " + path + " failed with ConnectException", e);
                }
                catch (IOException e) {
                    errorMessage = "IOException: " + e.getMessage();
                    Log.e(TAG, "POST " + path + " failed with IOException", e);
                } catch (Exception e) { // 그 외 예외 처리
                    errorMessage = "Exception: " + e.getMessage();
                    Log.e(TAG, "POST " + path + " failed with Exception", e);
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing BufferedReader for " + path, e);
                        }
                    }
                    if (conn != null) {
                        conn.disconnect();
                    }

                    // 콜백 호출
                    // listener가 null이 아닐 때만 호출합니다.
                    if (listener != null) {
                        if (responseJsonString != null) { // 성공 응답이 있는 경우
                            listener.onSuccess(responseJsonString);
                        } else { // 에러가 발생한 경우
                            listener.onError(errorMessage != null ? errorMessage : "Unknown error during POST request for " + path);
                        }
                    }
                }
            }
        }).start(); // 스레드 시작
    }

    // /get_ball_position 요청을 보내고 처리하는 별도의 메서드
    private void getBallPositionData() {
        sendGetRequest("/get_ball_position", new ApiResponseListener() {
            @Override

            public void onSuccess(String response) {
                Log.i(TAG, "/get_ball_position Success: " + response);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            int cx = jsonResponse.getInt("cx");
                            int cy = jsonResponse.getInt("cy");



                            // String ballPositionMessage = "공 위치: cx=" + cx + ", cy=" + cy;
                            // Log.d(TAG, ballPositionMessage);


                            // Toast.makeText(MainActivity.this, ballPositionMessage, Toast.LENGTH_LONG).show(); // Toast 대신 TextView 사용
                            if (tvBallPosition != null) {
                                tvBallPosition.setText("cx: " + cx + ", cy: " + cy); // TextView 업데이트
                            }

                            controlTemiMovementBasedOnCx(cx);

                        } catch (JSONException e) {
                            Log.e(TAG, "JSON parsing error for /get_ball_position: ", e);
                            Toast.makeText(MainActivity.this, "공 위치 데이터 파싱 오류", Toast.LENGTH_SHORT).show();
                            if (tvBallPosition != null) {
                                tvBallPosition.setText("cx: error, cy: error");
                            }
                        }
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "/get_ball_position Error: " + errorMessage);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "공 위치 가져오기 실패: " + errorMessage, Toast.LENGTH_SHORT).show();
                        if (tvBallPosition != null) {
                            tvBallPosition.setText("cx: error, cy: error");
                        }
                        // 오류 발생 시 로봇을 멈추거나 특정 행동을 하도록 추가할 수 있음
                        // 예: if (robot != null && robot.isReady()) robot.stopMovement();
                    }
                });
            }
        });
    }

    // cx 값에 따라 Temi 로봇의 이동을 제어하는 메서드
    private void controlTemiMovementBasedOnCx(int cx) {
        if (robot == null || !robot.isReady()) {
            Log.w(TAG, "Robot is not ready or null. Cannot control movement.");
            return;
        }

        int screenWidthCenter = 320; // 화면 가로 중앙값 (카메라 해상도 640 기준)
        int deadZone = 80;      // 중앙으로 간주할 오차 허용 범위
        float rotationSpeedFactor = 0.5f; // 회전 속도 인자
        float forwardSpeed = 0.3f;    // 직진 속도
        int rotationAngle = 5; // 기본 회전 각도

        Log.d(TAG, "controlTemiMovementBasedOnCx: cx = " + cx + ", isForcedStraightMode = " + isForcedStraightMode + ", lastCenterDetectionTimeMillis = " + lastCenterDetectionTimeMillis);

        if (cx == -1) { // 공을 찾지 못한 경우
            Log.w(TAG, "Ball not detected (cx = -1).");
            if (!isForcedStraightMode) { // 강제 직진 모드가 아닐 때만 멈춤
                Log.d(TAG, "Ball not detected and not in forced straight mode. Stopping movement.");
                robot.stopMovement();
            } else {
                // 강제 직진 모드 중 공을 놓친 경우:
                // 1. 즉시 멈춘다 (아래 로직과 유사하게 isForcedStraightMode = false 처리 후 stopMovement)
                // 2. 강제 직진 모드를 유지하며 계속 직진한다 (현재 로직)
                // 3. 강제 직진 모드를 즉시 해제하고, 다음 탐지 사이클에서 다시 판단한다.
                // 현재는 강제 직진 모드 중에는 cx = -1 이라도 STRAIGHT_DURATION_MS 동안은 계속 직진합니다.
                // 만약 공을 놓치면 즉시 멈추고 싶다면, 여기서 isForcedStraightMode = false; lastCenterDetectionTimeMillis = 0; robot.stopMovement(); 를 호출할 수 있습니다.
                Log.d(TAG, "Ball not detected BUT in forced straight mode. Continuing straight or finishing forced mode based on time.");
            }
            // 강제 직진 중이 아니라면, 여기서 반환하여 아래 로직을 실행하지 않음.
            // 단, 강제 직진 모드일 경우 아래 시간 체크 로직을 타야 하므로, 여기서 바로 return 하지 않도록 주의.
            // if (!isForcedStraightMode) return; // 이 줄을 추가하면, 강제 직진 아닐 때 공 놓치면 바로 끝
        }

        // 강제 직진 모드 확인 및 처리
        if (isForcedStraightMode) {
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime - lastCenterDetectionTimeMillis < STRAIGHT_DURATION_MS) {
                // 3초가 지나지 않았으면 계속 직진 (cx 값에 관계없이)
                Log.d(TAG, "Forced straight mode: Moving forward.");
                robot.skidJoy(forwardSpeed, 0);
                return; // 강제 직진 중이므로 아래 회전/직진 판단 로직 실행 안 함
            } else {
                // 3초가 지났으면 강제 직진 모드 해제
                Log.d(TAG, "Forced straight mode finished. Resetting flag.");
                isForcedStraightMode = false;
                lastCenterDetectionTimeMillis = 0;
                // 강제 직진 종료 후 바로 멈출지, 아니면 현재 cx 값으로 다시 판단할지는 정책에 따라 결정
                // robot.stopMovement(); // 필요하다면 여기서 한번 멈추고 아래에서 다시 판단
            }
        }

        // --- 강제 직진 모드가 아니거나, 강제 직진 모드가 방금 끝난 경우 아래 로직 실행 ---

        if (cx == -1 && !isForcedStraightMode) { // 강제 직진이 아니고, 공을 못찾았으면 여기서 멈추고 종료.
            Log.d(TAG, "Double check: Ball not detected (cx = -1) and not in forced straight mode after forced mode check. Stopping movement.");
            robot.stopMovement();
            return;
        }


        // 일반적인 공 위치 기반 제어 (강제 직진 모드가 아닐 때)
        if (cx < screenWidthCenter - deadZone) { // 공이 왼쪽에 있음
            Log.d(TAG, "Ball on the left (cx=" + cx + "). Turning left.");
            robot.turnBy(-rotationAngle, rotationSpeedFactor);
            // 회전 시에는 강제 직진 모드를 활성화하지 않음
            // isForcedStraightMode = false; // 이미 강제 직진 모드가 아니거나 해제된 상태일 것임
            // lastCenterDetectionTimeMillis = 0;
        } else if (cx > screenWidthCenter + deadZone) { // 공이 오른쪽에 있음
            Log.d(TAG, "Ball on the right (cx=" + cx + "). Turning right.");
            robot.turnBy(rotationAngle, rotationSpeedFactor);
            // 회전 시에는 강제 직진 모드를 활성화하지 않음
            // isForcedStraightMode = false;
            // lastCenterDetectionTimeMillis = 0;
        } else if (cx != -1) { // 공이 중앙 부근에 있음 (그리고 공이 감지된 상태 cx != -1)
            Log.d(TAG, "Ball in the center (cx=" + cx + "). Moving forward AND starting/confirming forced straight mode.");
            robot.skidJoy(forwardSpeed, 0); // 직진

            // 공이 중앙에 감지되었으므로 "강제 직진 모드" 시작/갱신
            // (이미 강제 직진 모드가 해제되었더라도, 다시 중앙으로 오면 새로 시작)
            if (!isForcedStraightMode) { // 아직 강제 직진 모드가 아니었다면, 새로 시작
                Log.d(TAG, "Ball centered, starting new forced straight mode.");
                isForcedStraightMode = true;
                lastCenterDetectionTimeMillis = SystemClock.elapsedRealtime(); // 현재 시간 기록
            } else {
                // 이 경우는 거의 발생하지 않음. (위에서 isForcedStraightMode = true 이면 return 되었을 것이므로)
                // 만약 강제 직진 모드인데 이 로직에 도달했고, 공이 중앙이라면 시간을 갱신할 수도 있지만,
                // 현재 로직 상으로는 isForcedStraightMode가 true일 때 위에서 return 되므로 여기까지 오지 않음.
                // Log.d(TAG, "Ball centered, already in forced straight mode. Time might be updated if logic changes.");
                // lastCenterDetectionTimeMillis = SystemClock.elapsedRealtime(); // 강제 직진 중 중앙 재확인 시 시간 갱신 (선택적)
            }
        } else {
            // cx == -1 이고 isForcedStraightMode == false 인 경우 (위에서 이미 처리되었어야 함)
            // 안전장치로 로봇 멈춤
            Log.w(TAG, "Ball not detected (cx=" + cx + ") and not in forced straight. Should have been caught earlier. Stopping movement.");
            robot.stopMovement();
        }
    }

    // HTTP GET 요청을 보내는 메서드 (이전 답변에서 제공한 것과 거의 동일)
    private void sendGetRequest(final String path, final ApiResponseListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                BufferedReader reader = null;
                String responseJsonString = null;
                String errorMessage = null;

                try {
                    URL url = new URL(BASE_URL + path);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3000); // 연결 타임아웃 3초
                    conn.setReadTimeout(3000);    // 읽기 타임아웃 3초
                    conn.setDoInput(true); // 응답을 받기 위함

                    int responseCode = conn.getResponseCode();
                    Log.d(TAG, path + " Response Code: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = conn.getInputStream();
                        StringBuilder buffer = new StringBuilder();
                        if (inputStream == null) {
                            errorMessage = "No input stream from server for GET.";
                            Log.w(TAG, errorMessage + " for path: " + path);
                        } else {
                            reader = new BufferedReader(new InputStreamReader(inputStream));
                            String line;
                            while ((line = reader.readLine()) != null) {
                                buffer.append(line);
                            }
                            if (buffer.length() == 0) {
                                Log.w(TAG, "Empty response from server for GET path: " + path + " (HTTP_OK)");
                                // 서버가 빈 응답을 보냈지만 정상(200 OK)인 경우, 빈 JSON 객체로 처리하거나
                                // 또는 에러로 간주할 수 있습니다. 여기서는 빈 객체로 가정합니다.
                                responseJsonString = "{}";
                            } else {
                                responseJsonString = buffer.toString();
                            }
                        }
                    } else {
                        errorMessage = "Server returned non-OK status for GET: " + responseCode;
                        Log.e(TAG, errorMessage + " for path: " + path);
                        InputStream errorStream = conn.getErrorStream();
                        if (errorStream != null) {
                            BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
                            StringBuilder errorBuffer = new StringBuilder();
                            String errorLine;
                            while ((errorLine = errorReader.readLine()) != null) {
                                errorBuffer.append(errorLine);
                            }
                            Log.e(TAG, "Error stream content for GET " + path + ": " + errorBuffer.toString());
                            errorMessage += " - Details: " + errorBuffer.toString();
                            errorReader.close();
                        }
                    }
                } catch (java.net.SocketTimeoutException e) {
                    errorMessage = "GET Timeout: " + e.getMessage();
                    Log.e(TAG, "GET " + path + " failed with SocketTimeoutException", e);
                } catch (java.net.ConnectException e) {
                    errorMessage = "GET Connection failed: " + e.getMessage();
                    Log.e(TAG, "GET " + path + " failed with ConnectException", e);
                } catch (IOException e) {
                    errorMessage = "GET IOException: " + e.getMessage();
                    Log.e(TAG, "GET " + path + " failed with IOException", e);
                } catch (Exception e) {
                    errorMessage = "GET Exception: " + e.getMessage();
                    Log.e(TAG, "GET " + path + " failed with Exception", e);
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing BufferedReader for GET " + path, e);
                        }
                    }
                    if (conn != null) {
                        conn.disconnect();
                    }

                    // 콜백 호출
                    if (listener != null) {
                        if (responseJsonString != null) {
                            listener.onSuccess(responseJsonString);
                        } else {
                            listener.onError(errorMessage != null ? errorMessage : "Unknown error during GET request for " + path);
                        }
                    }
                }
            }
        }).start(); // 스레드 시작
    }


    @Override
    protected void onStart() {
        super.onStart();
        //listeners.initListeners();
        robot.addOnRobotReadyListener(this);
        robot.addNlpListener(this);
        robot.addOnBeWithMeStatusChangedListener(this);
        robot.addOnGoToLocationStatusChangedListener(this);
        robot.addConversationViewAttachesListenerListener(this);
        robot.addWakeupWordListener(this);
        robot.addTtsListener(this);
        robot.addOnLocationsUpdatedListener(this);
        robot.addOnConstraintBeWithStatusChangedListener(this);
        robot.addOnDetectionStateChangedListener(this);
        robot.addAsrListener(this);

        Log.d(TAG, "Listeners registered in onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        //listeners.onStop();
        robot.removeOnRobotReadyListener(this);
        robot.removeNlpListener(this);
        robot.removeOnBeWithMeStatusChangedListener(this);
        robot.removeOnGoToLocationStatusChangedListener(this);
        robot.removeConversationViewAttachesListenerListener(this);
        robot.removeWakeupWordListener(this);
        robot.removeTtsListener(this);
        robot.removeOnLocationsUpdateListener(this);
        robot.removeDetectionStateChangedListener(this);
        robot.removeAsrListener(this);
        if (robot != null) { // robot 인스턴스가 null이 아닐 때만 호출
            robot.stopMovement();
            Log.d(TAG, "Robot movement stopped in onStop");
        }

        // 주기적인 움직임 중단 (앱이 백그라운드로 갈 때)
        stopPeriodicMovement(); // 추가됨: onStop 시 주기적 작업 중단
        Log.d(TAG, "Listeners removed and periodic movement stopped in onStop");
    }

    // 액티비티가 파괴되기 직전에 호출
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Handler에 예약된 콜백 제거 (메모리 누수 방지)
        if (movementHandler != null && movementRunnable != null) {
            movementHandler.removeCallbacks(movementRunnable);
        }
        Log.d(TAG, "Movement callbacks removed in onDestroy");
    }


    public void Pick() { // 흡입 관련 알고리즘 구현

        //robot.skidJoy(500, 500);
        //robot.turnBy(10);

        Log.d(TAG, "Pick action triggered");
        // Toast.makeText(this, "Pick action: Turning robot", Toast.LENGTH_SHORT).show(); // 사용자 피드백 (선택 사항)
    }

    public void Pong() { // 홈베이스 복귀 알고리즘 구현

        Log.d(TAG, "Pong action triggered: Stopping movement and attempting to go to homebase.");
        if (robot != null && robot.isReady()) {
            robot.stopMovement(); // 로봇 이동 즉시 중지
            robot.goTo("홈베이스"); // "홈베이스"라는 저장된 위치로 이동 시도
        } else {
            Log.w(TAG, "Pong action: Robot not ready or null.");
            Toast.makeText(this, "로봇이 준비되지 않아 홈베이스로 이동할 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onPublish(@NonNull ActivityStreamPublishMessage activityStreamPublishMessage) {

    }

    @Override
    public void onAsrResult(@NonNull String s) {

    }

    @Override
    public void onConversationAttaches(boolean b) {

    }

    @Override
    public void onNlpCompleted(@NonNull NlpResult nlpResult) {

    }

    @Override
    public void onTtsStatusChanged(@NonNull TtsRequest ttsRequest) {

    }

    @Override
    public void onWakeupWord(@NonNull String s, int i) {

    }

    @Override
    public void onBeWithMeStatusChanged(@NonNull String s) {

    }

    @Override
    public void onConstraintBeWithStatusChanged(boolean b) {

    }

    @Override
    public void onDetectionStateChanged(int i) {
        Log.d(TAG, "onDetectionStateChanged: state=" + i);
    }

    @Override
    public void onGoToLocationStatusChanged(@NonNull String location, @NonNull String status, int descriptionId, @NonNull String description) {
        // 로봇이 특정 저장된 위치로 이동하는 과정(시작, 계산 중, 이동 중, 완료, 중단)에 대한 상태 변경을 알려줍니다.
        Log.d(TAG, "onGoToLocationStatusChanged: location=" + location + ", status=" + status + ", descriptionId=" + descriptionId + ", description=" + description);

        // status 문자열은 OnGoToLocationStatusChangedListener에 정의된 상수들과 비교할 수 있습니다.
        // 예: OnGoToLocationStatusChangedListener.START, OnGoToLocationStatusChangedListener.CALCULATING,
        // OnGoToLocationStatusChangedListener.GOING, OnGoToLocationStatusChangedListener.COMPLETE, OnGoToLocationStatusChangedListener.ABORT

        if (location.equalsIgnoreCase("홈베이스")) { // 목표 위치가 "홈베이스"인 경우
            if (status.equalsIgnoreCase(OnGoToLocationStatusChangedListener.COMPLETE)) {
                Toast.makeText(this, "홈베이스 도착 완료!", Toast.LENGTH_SHORT).show();
                // 홈베이스 도착 후 추가 작업 (예: 충전 시작 알림, 다음 작업 대기 등)
            } else if (status.equalsIgnoreCase(OnGoToLocationStatusChangedListener.ABORT)) {
                Toast.makeText(this, "홈베이스 이동 중단: " + description, Toast.LENGTH_LONG).show();
            }
        }
        // descriptionId를 사용하여 실패 또는 중단 이유를 더 구체적으로 파악할 수도 있습니다.
        // 예: if (descriptionId == OnGoToLocationStatusChangedListener.DESCRIPTION_REASON_PATH_NOT_FOUND) { ... }
        // 예: if (descriptionId == OnGoToLocationStatusChangedListener.DESCRIPTION_REASON_REPOSSESSION_REQUIRED) { ... }

    }

    @Override
    public void onLocationsUpdated(@NonNull List<String> locations) {
        Log.d(TAG, "onLocationsUpdated: " + locations.toString());
    }

    @Override
    public void onRobotReady(boolean isReady) {

        // 로봇 SDK가 사용 준비가 되었는지(isReady = true) 또는
        // 연결이 해제되거나 사용할 수 없는 상태인지(isReady = false)를 알려주는 핵심 콜백입니다.
        if (isFinishing() || isDestroyed()) { // 액티비티가 종료 중이거나 이미 파괴된 경우, 작업 중단
            Log.w(TAG, "onRobotReady called but Activity is finishing or destroyed.");
            return;
        }

        if (isReady) { // 로봇 SDK가 준비되었을 때
            Log.i(TAG, "Robot is ready.");
            try {
                // 현재 액티비티 정보를 사용하여 로봇 SDK를 시작합니다.
                // 이는 로봇의 기능을 앱과 동기화하고, SDK가 액티비티의 컨텍스트를 인식하도록 합니다.
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                robot.onStart(activityInfo); // Temi SDK에 현재 액티비티를 알리고 SDK 서비스 시작
                Log.i(TAG, "Robot SDK onStart() called successfully.");

                // 로봇이 준비되었을 때 특정 초기 동작을 수행할 수 있습니다.
                // 예를 들어, 특정 위치로 이동하거나, 사용자에게 안내 메시지를 음성으로 전달할 수 있습니다.
                // robot.goTo("대기 장소");
                // robot.speak(TtsRequest.create("안녕하세요, 무엇을 도와드릴까요?", false));

            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "PackageManager.NameNotFoundException in onRobotReady", e);
                Toast.makeText(this, "로봇 초기화 오류: 액티비티 정보를 찾을 수 없습니다.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Error in onRobotReady during robot.onStart()", e);
                Toast.makeText(this, "로봇 초기화 중 알 수 없는 오류 발생.", Toast.LENGTH_LONG).show();
            }
        } else { // 로봇 SDK가 준비되지 않았거나 연결이 끊겼을 때
            Log.w(TAG, "Robot is not ready.");
            Toast.makeText(this, "로봇 연결이 해제되었거나 준비되지 않았습니다.", Toast.LENGTH_SHORT).show();
            // 로봇이 준비되지 않으면 로봇과 관련된 주기적인 작업(예: 공 추적)을 중단해야 합니다.
            stopPeriodicMovement();
        }

        /*
        if (isFinishing() || isDestroyed()) { // Activity가 종료 중이거나 파괴된 경우
            Log.w("MainActivity", "onRobotReady called but Activity is finishing or destroyed.");
            return;
        }

        if (isReady) {
            try {
                // getPackageManager()와 getComponentName()는 Activity의 메서드이므로 바로 사용 가능
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                robot.onStart(activityInfo);
                Log.i("MainActivity", "Robot started successfully.");
            } catch (PackageManager.NameNotFoundException e) {
                Log.e("MainActivity", "PackageManager.NameNotFoundException", e);
            } catch (Exception e) {
                Log.e("MainActivity", "Error in onRobotReady", e);
            }
        }*/
    }
}