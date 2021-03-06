package nicktxd.bluetoothgraph;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;

public class MainActivity extends Activity {

    private int zero;
    private int sensetivity;
    private double acceleration;

    private int lastType = 0;
    private int curType = 0;
    private int typeOfData = 0;

    private SharedPreferences sharedPref;

    private double graphLastXValue = 0;
    private int maxCountValues;

    private class objToSave {
        private LineGraphSeries<DataPoint> series;
        private double graphLastXValue;
        private boolean autoScroll;
        private boolean pause;
        private boolean connected;
        private boolean debug;
        private int typeOfData;
    }

    OnSharedPreferenceChangeListener settingsChangedListener =
        new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String s) {
            getSettings();
        }
    };
    private LineGraphSeries<DataPoint> series;
    private ToggleButton tbtScroll;
    private ToggleButton tbtPause;
    private boolean connected = false;
    private boolean debug = false;
    private boolean autoScroll = true;
    private boolean pause = false;
    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.tbScroll:
                    autoScroll = tbtScroll.isChecked();
                    break;
                case R.id.tbPause:
                    pause = tbtPause.isChecked();
                    break;
                case R.id.bReset:
                    resetGraph();
                    break;
            }
        }
    };
    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {

                case BluetoothActivity.SUCCESS_CONNECT:
                    BluetoothActivity.connectedThread =
                        new BluetoothActivity.ConnectedThread(
                            (BluetoothSocket) msg.obj);
                    BluetoothActivity.connectedThread.start();
                    setTitle(BluetoothActivity.getSelectedDevice().getName());
                    connected = true;
                    break;

                case BluetoothActivity.MESSAGE_READ:
                    String strIncom = (String) msg.obj;
                    if (strIncom == null)
                        break;
                    if (strIncom.indexOf('s') == 0) {
                        boolean flag = true;
                        try {
                            strIncom = strIncom.replace(
                                "s", "");
                            strIncom = strIncom.substring(0,
                                strIncom.indexOf('\n'));
                        } catch (Exception e) {
                            flag = false;
                        }
                        if (isFloatNumber(strIncom) && flag && !pause) {
                            double rawData = Double.parseDouble(strIncom);
                            double procData = 0;
                            switch (typeOfData) {
                                case 0:
                                    procData = rawData;
                                    curType = 0;
                                    break;
                                case 1:
                                    procData = rawToG(rawData);
                                    curType = 1;
                                    break;
                                case 2:
                                    procData = rawToAc(rawData);
                                    curType = 2;
                                    break;
                            }
                            if (curType != lastType) {
                                resetGraph();
                                lastType = curType;
                            }

                            try {
                                series.appendData(new DataPoint(
                                    graphLastXValue, procData), autoScroll,
                                    maxCountValues);
                            } catch (Exception ignored) {
                                System.out.println(ignored);
                            }

                            graphLastXValue += 1;
                        }
                    }
                    break;

                case BluetoothActivity.DISCONNECTED:
                    connected = false;
                    setTitle(getString(R.string.app_name));
                    break;

                case BluetoothActivity.CONNECTION_LOST:
                    Toast.makeText(getApplicationContext(),
                            "Connection lost", Toast.LENGTH_LONG).show();
                    BluetoothActivity.disconnect();
                    break;
            }
        }

        boolean isFloatNumber(String num) {
            try {
                Double.parseDouble(num);
            } catch (NumberFormatException nfe) {
                return false;
            }
            return true;
        }
    };
    private objToSave saveState;
    private objToSave retState;

    @Override
    public Object onRetainNonConfigurationInstance() {
        saveState.series = series;
        saveState.graphLastXValue = graphLastXValue;
        saveState.autoScroll = autoScroll;
        saveState.pause = pause;
        saveState.connected = connected;
        saveState.debug = debug;
        saveState.typeOfData = typeOfData;

        return saveState;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        BluetoothActivity.gethandler(mHandler);
        saveState = new objToSave();
        retState = (objToSave) getLastNonConfigurationInstance();
        graphInit();
        Init();
        getSettings();
    }

    void graphInit() {

        GraphView graph = (GraphView) findViewById(R.id.graph);

        //graph.getViewport().setXAxisBoundsManual(true);
        //graph.getViewport().setYAxisBoundsManual(true);

        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(true);
        graph.getViewport().setScrollable(true);
        graph.getViewport().setScrollableY(true);

        graph.getViewport().setMinY(-2000);
        graph.getViewport().setMaxY(2000);
        graph.getViewport().setMaxX(50);

        Paint paint = new Paint();
        paint.setColor(getResources().getColor(R.color.colorPrimary));
        paint.setStrokeWidth(5);
        paint.setAntiAlias(true);

        /*
        graph.getGridLabelRenderer().setVerticalAxisTitle("Data");
        graph.getGridLabelRenderer().setHorizontalAxisTitle(
            "Number of data point");
        graph.getGridLabelRenderer().setVerticalAxisTitleTextSize(35);
        graph.getGridLabelRenderer().setHorizontalAxisTitleTextSize(35);
        graph.getGridLabelRenderer().setLabelVerticalWidth(80);
        graph.getGridLabelRenderer().setTextSize(30);
        */


        if (retState == null) {
            series = new LineGraphSeries<>();
            series.setDrawDataPoints(true);
            series.setDataPointsRadius(5);
            series.setThickness(4);
            series.setCustomPaint(paint);
        } else {
            series = retState.series;
            graphLastXValue = retState.graphLastXValue;
        }

        graph.addSeries(series);
        series.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                Toast.makeText(getApplicationContext(),
                    "On Data Point clicked: " + dataPoint.getY(),
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    void Init() {


        Button btReset = (Button) findViewById(R.id.bReset);
        btReset.setOnClickListener(onClickListener);
        tbtScroll = (ToggleButton) findViewById(R.id.tbScroll);
        tbtScroll.setChecked(true);
        tbtScroll.setOnClickListener(onClickListener);
        tbtPause = (ToggleButton) findViewById(R.id.tbPause);
        tbtPause.setChecked(false);
        tbtPause.setOnClickListener(onClickListener);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        getSettings();

        sharedPref.registerOnSharedPreferenceChangeListener(
            settingsChangedListener);

        if (retState != null) {
            typeOfData = retState.typeOfData;
            autoScroll = retState.autoScroll;
            tbtScroll.setChecked(autoScroll);
            pause = retState.pause;
            tbtPause.setChecked(pause);
            connected = retState.connected;
            debug = retState.debug;
            if (connected) {
                setTitle(BluetoothActivity.getSelectedDevice().getName());
            }
            if (debug) {
                setTitle("Debug");
            }
        }
    }

    void resetGraph() {
        series.resetData(new DataPoint[]{new DataPoint(0, 0)});
        graphLastXValue = 0;
        series.appendData(new DataPoint(0, 0), autoScroll, maxCountValues);
    }

    double rawToG(double rawData) {
        return (rawData - zero) / sensetivity;
    }

    double rawToAc(double rawData) {
        return (rawData - zero) * acceleration / sensetivity;
    }

    void getSettings() {
        try {
            maxCountValues = Integer.parseInt(sharedPref.getString(
                "max_count_values", "500"));
            typeOfData = Integer.parseInt(sharedPref.getString(
                "type_of_data", "0"));
            zero = Integer.parseInt(sharedPref.getString("zero", "2048"));
            sensetivity = Integer.parseInt(sharedPref.getString(
                "sensetivity", "200"));
            acceleration = Double.parseDouble(sharedPref.getString(
                "acceleration", "9.8"));
        } catch (Exception ignored) {
            System.out.println(ignored);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.bluetooth:
                if (connected) {
                    Toast.makeText(getApplicationContext(),
                        "Connection already established",
                        Toast.LENGTH_SHORT).show();
                    break;
                }
                if (debug) {
                    Toast.makeText(getApplicationContext(),
                        "Debug already started", Toast.LENGTH_SHORT).show();
                    break;
                }
                startActivity(new Intent("android.intent.action.BT"));
                break;
            case R.id.settings:
                startActivity(new Intent("android.intent.action.SET"));
                break;
            case R.id.Disconnect:
                if (connected) {
                    BluetoothActivity.disconnect();
                    connected = false;
                    setTitle(getString(R.string.app_name));
                }
                if (debug) {
                    BluetoothActivity.stopDebug();
                    debug = false;
                    setTitle(getString(R.string.app_name));

                }
                break;
            case R.id.Debug:
                if (connected) {
                    Toast.makeText(getApplicationContext(),
                        "Connection already established",
                        Toast.LENGTH_SHORT).show();
                    break;
                }
                if (debug) {
                    Toast.makeText(getApplicationContext(),
                        "Debug already started",
                        Toast.LENGTH_SHORT).show();
                    break;
                }
                BluetoothActivity.debugThread =
                    new BluetoothActivity.DebugThread();
                BluetoothActivity.debugThread.start();
                setTitle(getString(R.string.debug));
                debug = true;
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}