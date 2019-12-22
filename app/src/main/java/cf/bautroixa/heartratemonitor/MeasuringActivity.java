package cf.bautroixa.heartratemonitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NavUtils;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import cf.bautroixa.heartratemonitor.algorithm.ImageProcessing;
import cf.bautroixa.heartratemonitor.data.HeartRateConstant;
import cf.bautroixa.heartratemonitor.theme.GotItDialog;

public class MeasuringActivity extends AppCompatActivity implements HeartRateConstant {

    public static final int PERMISSION_REQUEST_CODE = 1259;
    private Button btnStop;
    private TextView txtTempHrValue, txtTempHrBPM, txtHRGuideMessage;
    private LineChart chartHR;

    int chartLineColor, chartFillColor;

    ImageView imgHeart;
    ViewGroup.LayoutParams imgHeartLayoutParams;

    TutorialDialog tutorialDialog;

    private Handler handler = null;
    private Runnable finnishJob = null;
    private CircularProgressBar progressBar;
    private SurfaceView videoPreview;
    private SurfaceHolder previewHolder;
    private PowerManager.WakeLock wakeLock = null;
    private Camera camera = null;
    private static boolean onBeat = false;
    private static boolean started = false;
    private static boolean measuring = false;
    private static long lastBeatTime = 0;

    private static final AtomicBoolean processing = new AtomicBoolean(false);
    private static int beatsIndex = 0;
    private static final int beatsArraySize = 3;
    private static final int[] beatsArray = new int[beatsArraySize];
    private static double beats = 0;
    private static long startTime = 0;

    private static int averageIndex = 0;
    private static final int averageArraySize = 4;
    private static final int[] averageArray = new int[averageArraySize];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.heart_rate_measuring));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setElevation(0);

        if (isEmulator()) {
            initView();
            simulateHeartRateComplete(98);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                grantCameraPermission();
            } else {
                initView();
            }
        }

    }

    private void initView() {
        setContentView(R.layout.activity_heart_rate_measuring);

        // init value
        started = false;
        beats = 0;
        measuring = true;
        lastBeatTime = System.currentTimeMillis();

        // bind view
        txtTempHrValue = findViewById(R.id.tv_temp_hr_value);
        txtTempHrBPM = findViewById(R.id.tv_temp_bpm);
        txtHRGuideMessage = findViewById(R.id.tv_heart_rate_description);

        chartHR = (LineChart) findViewById(R.id.chart_heart_rate);

        imgHeart = (ImageView) findViewById(R.id.img_heart_rate);
        imgHeartLayoutParams = (ViewGroup.LayoutParams) imgHeart.getLayoutParams();

        progressBar = findViewById(R.id.progress_hr_measure);
        btnStop = findViewById(R.id.btn_stop);
        videoPreview = findViewById(R.id.video_preview);
        previewHolder = videoPreview.getHolder();

        // chartHR
        chartHR.setViewPortOffsets(0, 0, 0, 0);
        chartHR.setBackgroundColor(getResources().getColor(R.color.hrLightBackground));
        chartHR.getDescription().setEnabled(false);
        chartHR.setPinchZoom(false);
        chartHR.setTouchEnabled(false);
        chartHR.setDragEnabled(false);
        chartHR.setScaleEnabled(false);

        chartHR.setDrawGridBackground(false);
        chartHR.setMaxHighlightDistance(300);

        chartHR.getXAxis().setEnabled(false);
        chartHR.getAxisLeft().setEnabled(false);
        chartHR.getAxisRight().setEnabled(false);
        chartHR.getLegend().setEnabled(false);

        LineData data = new LineData();
        data.setDrawValues(false);

        chartLineColor = getResources().getColor(R.color.hrDarkRed);
        chartFillColor = getResources().getColor(R.color.hrRed);

        // add empty data
        chartHR.setData(data);
        chartHR.animateXY(2000, 2000);
        addEntry(40.0f);
        addEntry(90.0f);

        // HR monitor
        txtTempHrBPM.setText("");
        txtTempHrValue.setText("--");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "App:DoNotDimScreen");
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MeasuringActivity.this.cancelHeartRateMeasure();
            }
        });

        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        tutorialDialog = new TutorialDialog();
        tutorialDialog.show(getSupportFragmentManager(), "tutorial_dialog");
    }

    private void grantCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            // user lastly refuse camera permission
            GotItDialog permissionDialog = new GotItDialog(R.string.hr_cam_diag_title, R.string.hr_cam_diag_desc, new Runnable() {
                @Override
                public void run() {
                    ActivityCompat.requestPermissions(MeasuringActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            PERMISSION_REQUEST_CODE);
                }
            });
            permissionDialog.show(getSupportFragmentManager(), "permission_dialog");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), R.string.hr_cam_granted, Toast.LENGTH_SHORT).show();
                    initView();
                    Log.d("cam", "granted");
                } else {
                    Toast.makeText(getApplicationContext(), R.string.hr_cam_not_grant, Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
        }
    }

    public static class TutorialDialog extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            View v = inflater.inflate(R.layout.dialog_heart_rate_measuring_tutorial, null);
            ImageView imgHrTutorial = v.findViewById(R.id.img_hr_tutorial);
            AnimationDrawable frameAnimation = (AnimationDrawable) imgHrTutorial.getDrawable();
            frameAnimation.setCallback(imgHrTutorial);
            frameAnimation.setVisible(true, true);
            frameAnimation.start();
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setView(v)
                    // Add action buttons
                    .setPositiveButton(R.string.btn_got_it, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog1, int id) {
                            TutorialDialog.this.getDialog().cancel();
                        }
                    }).create();
            dialog.getWindow().setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_radius_full_white);
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog12) {
                    Button positiveButton = ((AlertDialog) dialog12).getButton(DialogInterface.BUTTON_POSITIVE);
                    positiveButton.setTextColor(TutorialDialog.this.getResources().getColor(R.color.white));
                    positiveButton.setBackgroundResource(R.drawable.btn_raised);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
                    positiveButton.setLayoutParams(params);
                    positiveButton.invalidate();
                }
            });
            return dialog;
        }
    }

    public void simulateHeartRateComplete(final int bpm) {
        txtTempHrBPM.setText("BPM");
        // auto intent into result screen
        handler = new Handler();
        finnishJob = new Runnable() {
            @Override
            public void run() {
                finishHeartRateMeasure(bpm);
            }
        };
        handler.postDelayed(finnishJob, 10000);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 20; i++) {
                    if (i== 10) tutorialDialog.dismiss();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            int rand = bpm - 5 + new Random().nextInt(10);
                            progressBar.setProgress(progressBar.getProgress() + 5);
                            txtTempHrValue.setText("" + rand);
                            addEntry(rand - 30.0f);
                            addEntry(rand);
                        }
                    });
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
    }

    public void cancelHeartRateMeasure() {
        finish();
        started = false;
        measuring = false;
        if (finnishJob != null && handler != null) {
            handler.removeCallbacks(finnishJob);
            finnishJob = null;
        }
    }

    public void finishHeartRateMeasure(int bpm) {
        Intent intent = new Intent(MeasuringActivity.this, ResultActivity.class);
        intent.putExtra(EXTRA_HEART_RATE_VALUE, bpm);
        startActivity(intent);
        finish();
    }

    private void addEntry(float value) {

        LineData data = chartHR.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), value), 0);


            data.notifyDataChanged();
            chartHR.notifyDataSetChanged();

            // limit the number of visible entries
            chartHR.setVisibleXRangeMaximum(10);
            // chartHR.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            chartHR.moveViewToX(data.getEntryCount());

            // this automatically refreshes the chartHR (calls invalidate())
            // chartHR.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private LineDataSet createSet() {
        LineDataSet set1 = new LineDataSet(null, "DataSet 1");

        set1.setDrawValues(false);
        set1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set1.setCubicIntensity(0.2f);
        set1.setDrawFilled(true);
        set1.setDrawCircles(false);
        set1.setLineWidth(1.8f);
        set1.setCircleRadius(4f);
//        set1.setCircleColor(chartLineColor);
        set1.setHighLightColor(getResources().getColor(R.color.hrWhiteBlue));
        set1.setColor(chartLineColor);
        set1.setFillColor(chartFillColor);
        set1.setFillAlpha(200);
        set1.setDrawHorizontalHighlightIndicator(false);
        set1.setFillFormatter(new IFillFormatter() {
            @Override
            public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                return chartHR.getAxisLeft().getAxisMinimum();
            }
        });
        return set1;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onResume() {
        Log.d("cam", "onREsume");
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("cam", "onREsume: permission not grant");
        } else {
            Log.d("cam", "onREsume: open cam");
            wakeLock.acquire(5 * 60 * 1000);
            camera = Camera.open();
            startTime = System.currentTimeMillis();
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        Log.d("cam", "onPause");
        super.onPause();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED && camera != null) {
            Log.d("cam", "onPause: release");
            wakeLock.release();
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                cancelHeartRateMeasure();
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private android.hardware.Camera.PreviewCallback previewCallback = new android.hardware.Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, android.hardware.Camera cam) {
            if (!measuring) return;
            if (data == null) throw new NullPointerException();
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) throw new NullPointerException();
            if (!processing.compareAndSet(false, true)) return;
            int width = size.width;
            int height = size.height;
            int imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), height, width);
            if (imgAvg == 0 || imgAvg == 255) {
                processing.set(false);
                return;
            }

            int averageArrayAvg = 0;
            int averageArrayCnt = 0;
            for (int i = 0; i < averageArray.length; i++) {
                if (averageArray[i] > 0) {
                    averageArrayAvg += averageArray[i];
                    averageArrayCnt++;
                }
            }
            int rollingAverage = (averageArrayCnt > 0) ? (averageArrayAvg / averageArrayCnt) : 0;
            if (started) {
                Log.d("heartrate step: ", "Thời gian đo:" + String.valueOf((System.currentTimeMillis() - startTime) / 1000) + " s");
            }
//            Log.d("heartrate step: ", String.valueOf(imgAvg));
            if (imgAvg < rollingAverage && imgAvg > 180) {
                if (lastBeatTime != 0) {
                    long curTime = System.currentTimeMillis();
                    long step = curTime - lastBeatTime;
                    if (step < 400) {
                        Log.d("heartrate step: ", String.valueOf(step));
                    } else {
                        lastBeatTime = curTime;
                        if (onBeat) {
                            beats++;
                            if (!started && beats == 4) {
                                started = true;
                                startTime = System.currentTimeMillis();
                            }
                            long endTime = System.currentTimeMillis();
                            double totalTimeInSecs = (endTime - startTime) / 1000d;
                            double bps = ((beats - 4) / totalTimeInSecs);
                            int dpm = (int) (bps * 60d);
                            if (beats == 14) {
                                progressBar.setProgressWithAnimation(100.0f);
                                txtTempHrValue.setText(String.valueOf(dpm));
                                txtTempHrBPM.setText("BPM");
                                started = false;
                                measuring = false;
                                finishHeartRateMeasure(dpm);
                            }
                            if (beats < 14) {
                                if (beats >= 4) {
                                    txtTempHrValue.setText(String.valueOf(dpm));
                                    progressBar.setProgressWithAnimation(((float) beats - 4) * 100 / 10);
                                    txtHRGuideMessage.setText(getString(R.string.heart_rate_measuring));
                                    tutorialDialog.dismiss();
                                    txtTempHrBPM.setText("BPM");
                                }
                            }
                            imgHeartLayoutParams.width = 30;
                            imgHeartLayoutParams.height = 30;
                            imgHeart.setLayoutParams(imgHeartLayoutParams);

                            if (dpm > 0) {
                                addEntry(dpm - 20.0f);
                                addEntry(dpm);
                            }

//                            beatImg.setImageResource(R.drawable.green_icon);
                            onBeat = false;
                            // Log.d(TAG, "BEAT!! beats="+beats);
                        }
                    }
                }

            } else if (imgAvg > rollingAverage && imgAvg > 180) {
                onBeat = true;
//                chartCurrentValue-=30.0f;
//                addEntry(chartCurrentValue);
                imgHeartLayoutParams.width = 50;
                imgHeartLayoutParams.height = 50;
                imgHeart.setLayoutParams(imgHeartLayoutParams);

//                beatImg.setImageResource(R.drawable.red_icon);
            }

            if (averageIndex == averageArraySize) averageIndex = 0;
            averageArray[averageIndex] = imgAvg;
            averageIndex++;
            processing.set(false);
        }

    };

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d("cam", "surfaceCreated");
            try {
                camera.setPreviewDisplay(previewHolder);
                camera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                //Log.e("PreviewDemo-surfaceCallback", "Exception in setPreviewDisplay()", t);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (camera == null) return;
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            Camera.Size size = getSmallestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                //   Log.d(TAG, "Using width=" + size.width + " height=" + size.height);
            }
            camera.setParameters(parameters);
            camera.startPreview();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };

    private Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea < resultArea) result = size;
                }
            }
        }

        return result;
    }

    public static boolean isEmulator() {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }

}
