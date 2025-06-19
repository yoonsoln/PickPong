package com.example.pickpongfine;

import static java.security.AccessController.getContext;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

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

import java.util.List;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.lang.ref.WeakReference;


public class RoboTemiListeners extends AppCompatActivity implements
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

    // private WeakReference<Context> contextRef = new WeakReference<>(null);

    public RoboTemiListeners() { this.robot = Robot.getInstance(); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(R.layout.your_activity_layout); // 레이아웃 설정
        // initListeners(); // 리스너 초기화 메서드 호출
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 리스너 등록은 onStart 또는 onResume 에서 하는 것이 좋습니다.
        initListeners();
    }

    public void initListeners() {
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

        // contextRef = new WeakReference<>(context);
    }

    /*
    public void stop() {
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
        robot.stopMovement();
    }
    */
    @Override
    protected void onStop() {
        super.onStop();
        // 리스너 해제
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
        robot.stopMovement();
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

    }

    @Override
    public void onLocationsUpdated(@NonNull List<String> list) {

    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onRobotReady(boolean isReady) {

        if (isFinishing() || isDestroyed()) { // Activity가 종료 중이거나 파괴된 경우
            Log.w("RoboTemiListeners", "onRobotReady called but Activity is finishing or destroyed.");
            return;
        }


        if (isReady) {
            try {
                // getPackageManager()와 getComponentName()는 Activity의 메서드이므로 바로 사용 가능
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                robot.onStart(activityInfo);
                Log.i("RoboTemiListeners", "Robot started successfully.");
            } catch (PackageManager.NameNotFoundException e) {
                Log.e("RoboTemiListeners", "PackageManager.NameNotFoundException", e);
            } catch (Exception e) {
                Log.e("RoboTemiListeners", "Error in onRobotReady", e);
            }
        }

        /*
        if (isFinishing() || isDestroyed()) { // Activity가 종료 중이거나 파괴된 경우
            return;
        }
        */

        /*
        Context context = contextRef.get();
        if (context == null) {
            Log.e("LISTENER", "Context is null in onRobotReady");
            return;
        } // 컨텍스트가 null이면 종료

        if (contextRef == null) { // WeakReference 자체가 null인 경우
            Log.e("RoboTemiListeners", "WeakReference is not initialized!");
            return;
        }

        PackageManager pm = context.getPackageManager();
        */


        /*
        if (isReady) {
            try {
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                robot.onStart(activityInfo);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        */
    }

    @Override
    public void onGoToLocationStatusChanged(@NonNull String s, @NonNull String s1, int i, @NonNull String s2) {

    }


}
