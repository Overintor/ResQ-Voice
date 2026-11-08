    package com.example.resq_voice;
    
    import android.Manifest;
    import android.annotation.SuppressLint;
    import android.app.AlertDialog;
    import android.bluetooth.BluetoothAdapter;
    import android.bluetooth.BluetoothManager;
    import android.bluetooth.le.AdvertiseCallback;
    import android.bluetooth.le.AdvertiseData;
    import android.bluetooth.le.AdvertiseSettings;
    import android.bluetooth.le.BluetoothLeAdvertiser;
    import android.bluetooth.le.BluetoothLeScanner;
    import android.bluetooth.le.ScanCallback;
    import android.bluetooth.le.ScanResult;
    import android.bluetooth.le.ScanSettings;
    import android.content.BroadcastReceiver;
    import android.content.Context;
    import android.content.Intent;
    import android.content.IntentFilter;
    import android.content.pm.PackageManager;
    import android.content.res.ColorStateList;
    import android.graphics.Color;
    import android.media.AudioManager;
    import android.media.ToneGenerator;
    import android.os.Build;
    import android.os.Bundle;
    import android.os.Handler;
    import android.os.Looper;
    import android.os.VibrationEffect;
    import android.os.Vibrator;
    import android.speech.tts.TextToSpeech;
    import android.speech.tts.UtteranceProgressListener;
    import android.view.Gravity;
    import android.widget.Button;
    import android.widget.ImageButton;
    import android.widget.ImageView;
    import android.widget.TextView;
    import android.widget.Toast;
    
    import androidx.annotation.NonNull;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.core.app.ActivityCompat;
    import androidx.core.view.GravityCompat;
    import androidx.drawerlayout.widget.DrawerLayout;
    
    import android.hardware.Sensor;
    import android.hardware.SensorEvent;
    import android.hardware.SensorEventListener;
    import android.hardware.SensorManager;
    
    import java.nio.charset.StandardCharsets;
    import java.util.Locale;
    
    @SuppressWarnings("deprecation")
    public class MainActivity extends AppCompatActivity
            implements TextToSpeech.OnInitListener {
        // ============================================================
        // AUTO FALL / IMPACT DETECTION (ACCELEROMETER)
        // ============================================================
        private android.hardware.SensorManager sensorManager;
        private android.hardware.Sensor accelerometer;
        private android.hardware.SensorEventListener sensorEventListener;
        private boolean isFallDetectionActive = true;
        private AlertDialog fallCountdownDialog = null;
        private final Handler fallHandler = new Handler(Looper.getMainLooper());
        private Runnable fallCountdownRunnable;
        private int remainingSeconds = 10;
        // ============================================================
        // UI
        // ============================================================
    
        private TextToSpeech tts;
    
        private DrawerLayout drawerLayout;
    
        private ImageButton btnMenu;
    
        private Button btnAction;
        private Button btnCall1669;
        private Button btnGpsShare;
        private Button btnFlashlight;
        private Button btnMedicalId;
        private Button btnLanguage;
    
        private Button menuCpr;
        private Button menuChoking;
        private Button menuBleeding;
        private Button menuSeizure;
    
        private TextView tvCurrentTopic;
        private TextView tvInstruction;
        private TextView tvBleStatus;
        private TextView tvCurrentLanguage;
        private ImageView imgGuide;
    
        // ============================================================
        // MODE
        // ============================================================
    
        private String currentMode = "CPR";
    
        private boolean isActionActive = false;
    
        private long startTimeMillis = 0;
    
        private int cprStep = 0;
    
        // ขั้นตอนคำแนะนำของ CHOKING / BLEEDING / SEIZURE
        private int guidanceStep = 0;
    
    
    
        // ============================================================
        // LANGUAGE
        // ============================================================
    
        /*
         * true  = ภาษาไทย
         * false = English
         */
        private boolean isThai = true;
    
        // ============================================================
        // AUDIO
        // ============================================================
    
        private ToneGenerator toneGen;
    
        private Handler metronomeHandler;
    
        private Runnable metronomeRunnable;
    
        private Handler stepHandler;
    
        private AudioManager audioManager;
    
        // ============================================================
        // BLE SENDER
        // ============================================================
    
        private BluetoothAdapter bluetoothAdapter;
    
        private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    
        private AdvertiseCallback advertiseCallback;
    
        private boolean isBleAdvertising = false;
    
        // ============================================================
        // BLE RECEIVER
        // ============================================================
    
        private BluetoothLeScanner bluetoothLeScanner;
    
        private ScanCallback scanCallback;
    
        private boolean isScanning = false;
    
        private boolean hasAlertedForThisEvent = false;
    
        // ============================================================
        // CONSTANTS
        // ============================================================
    
        private static final int PERMISSION_REQUEST_CODE = 100;
    
        private static final int RESQ_MANUFACTURER_ID = 0x02E5;
    
        private final int COLOR_RED =
                Color.parseColor("#DC2626");
    
        private final int COLOR_GRAY =
                Color.parseColor("#475569");
    
        // ============================================================
        // BLUETOOTH STATE RECEIVER
        // ============================================================
    
        private final BroadcastReceiver bluetoothStateReceiver =
                new BroadcastReceiver() {
    
                    @Override
                    public void onReceive(
                            Context context,
                            Intent intent) {
    
                        if (!BluetoothAdapter.ACTION_STATE_CHANGED
                                .equals(intent.getAction())) {
    
                            return;
                        }
    
                        int state =
                                intent.getIntExtra(
                                        BluetoothAdapter.EXTRA_STATE,
                                        BluetoothAdapter.ERROR
                                );
    
                        if (state ==
                                BluetoothAdapter.STATE_ON) {
    
                            updateBleStatus(
                                    isThai
                                            ? "🟢 BLE: Bluetooth เปิดแล้ว กำลังเริ่ม Scanner..."
                                            : "🟢 BLE: Bluetooth is on. Starting scanner..."
                            );
    
                            new Handler(
                                    Looper.getMainLooper()
                            ).postDelayed(
                                    () -> {
    
                                        if (!isFinishing()) {
    
                                            startBystanderScanner();
                                        }
    
                                    },
                                    500
                            );
    
                        } else if (state ==
                                BluetoothAdapter.STATE_OFF) {
    
                            stopBystanderScanner();
    
                            updateBleStatus(
                                    isThai
                                            ? "⚠️ BLE: Bluetooth ปิดอยู่"
                                            : "⚠️ BLE: Bluetooth is off"
                            );
                        }
                    }
                };
    
        // ============================================================
        // ON CREATE
        // ============================================================
    
        @Override
        protected void onCreate(
                Bundle savedInstanceState) {
    
            super.onCreate(savedInstanceState);
    
            setContentView(
                    R.layout.activity_main
            );
            // ========================================================
            // 📌 1. โหลดค่าภาษาที่จำไว้ถาวร
            // ========================================================
            isThai = getSharedPreferences("ResQPrefs", MODE_PRIVATE).getBoolean("***", true);
    
            // รับค่าโหมดที่ส่งมาจากหน้า HomeActivity
            String selectedMode = getIntent().getStringExtra("SELECTED_MODE");
            if (selectedMode != null) {
                currentMode = selectedMode;
            }
    
            // --------------------------------------------------------
            // UI
            // --------------------------------------------------------
    
            drawerLayout =
                    findViewById(R.id.drawerLayout);
    
            btnMenu =
                    findViewById(R.id.btnMenu);
    
            btnAction =
                    findViewById(R.id.btnAction);
    
            btnCall1669 =
                    findViewById(R.id.btnCall1669);
    
            btnGpsShare =
                    findViewById(R.id.btnGpsShare);
    
            btnFlashlight =
                    findViewById(R.id.btnFlashlight);
    
            btnMedicalId =
                    findViewById(R.id.btnMedicalId);
    
            btnLanguage =
                    findViewById(R.id.btnLanguage);
    
            tvCurrentTopic =
                    findViewById(R.id.tvCurrentTopic);
    
            tvInstruction =
                    findViewById(R.id.tvInstruction);
    
            tvBleStatus =
                    findViewById(R.id.tvBleStatus);
    
            tvCurrentLanguage =
                    findViewById(R.id.tvCurrentLanguage);
    
            imgGuide =
                    findViewById(R.id.imgGuide);
    
            // --------------------------------------------------------
            // Drawer
            // --------------------------------------------------------
    
            menuCpr =
                    findViewById(R.id.menuCpr);
    
            menuChoking =
                    findViewById(R.id.menuChoking);
    
            menuBleeding =
                    findViewById(R.id.menuBleeding);
    
            menuSeizure =
                    findViewById(R.id.menuSeizure);
    
            // --------------------------------------------------------
            // TTS
            // --------------------------------------------------------
    
            tts =
                    new TextToSpeech(
                            this,
                            this
                    );
    
            // --------------------------------------------------------
            // TTS Listener
            //
            // ใช้ตรวจว่าเสียงพูด "จบจริง" แล้วจึงปขั้นตอนถัดไป
            // --------------------------------------------------------
    
            if (tts != null) {
    
                tts.setOnUtteranceProgressListener(
                        new UtteranceProgressListener() {
    
                            @Override
                            public void onStart(String utteranceId) {
                            }
    
                            @Override
                            public void onDone(String utteranceId) {
    
                                runOnUiThread(() -> {
    
                                    if (utteranceId == null || !isActionActive) {
                                        return;
                                    }
    
                                    // CPR: พูดจบทีละขั้น แล้วค่อยไปขั้นถัดไป
                                    if (utteranceId.startsWith("CPR_STEP_1") &&
                                            currentMode.equals("CPR")) {
                                        startCprStep2();
                                        return;
                                    }
    
                                    if (utteranceId.startsWith("CPR_STEP_2") &&
                                            currentMode.equals("CPR")) {
                                        startCprStep3();
                                        return;
                                    }
    
                                    // CPR: ต้องพูด 3...2...1... จบก่อนจึงเริ่มเสียงตี๊ด
                                    if (utteranceId.startsWith("CPR_STEP_3") &&
                                            currentMode.equals("CPR") &&
                                            cprStep == 3) {
                                        startMetronome();
                                        return;
                                    }
    
                                    // 3 เคสอื่น: พูดจบหนึ่งขั้นก่อนอ่านขั้นถัดไป
                                    if (utteranceId.startsWith("CHOKING_STEP_") &&
                                            currentMode.equals("CHOKING")) {
                                        advanceChokingGuidance();
                                        return;
                                    }
    
                                    if (utteranceId.startsWith("BLEEDING_STEP_") &&
                                            currentMode.equals("BLEEDING")) {
                                        advanceBleedingGuidance();
                                        return;
                                    }
    
                                    if (utteranceId.startsWith("SEIZURE_STEP_") &&
                                            currentMode.equals("SEIZURE")) {
                                        advanceSeizureGuidance();
                                    }
                                });
                            }
    
                            @Override
                            public void onError(String utteranceId) {
    
                                // หาก TTS error ให้ไปต่ออย่างปลอดภัยโดยไม่ทำให้ระบบค้าง
                                runOnUiThread(() -> {
    
                                    if (utteranceId == null || !isActionActive) {
                                        return;
                                    }
    
                                    if (utteranceId.startsWith("CPR_STEP_1") &&
                                            currentMode.equals("CPR")) {
                                        startCprStep2();
                                    } else if (utteranceId.startsWith("CPR_STEP_2") &&
                                            currentMode.equals("CPR")) {
                                        startCprStep3();
                                    } else if (utteranceId.startsWith("CPR_STEP_3") &&
                                            currentMode.equals("CPR") &&
                                            cprStep == 3) {
                                        startMetronome();
                                    } else if (utteranceId.startsWith("CHOKING_STEP_") &&
                                            currentMode.equals("CHOKING")) {
                                        advanceChokingGuidance();
                                    } else if (utteranceId.startsWith("BLEEDING_STEP_") &&
                                            currentMode.equals("BLEEDING")) {
                                        advanceBleedingGuidance();
                                    } else if (utteranceId.startsWith("SEIZURE_STEP_") &&
                                            currentMode.equals("SEIZURE")) {
                                        advanceSeizureGuidance();
                                    }
                                });
                            }
    
                            @Override
                            public void onStop(String utteranceId, boolean interrupted) {
                            }
                        }
                );
            }
    
            // --------------------------------------------------------
            // Tone Generator
            // --------------------------------------------------------
    
            toneGen =
                    new ToneGenerator(
                            AudioManager.STREAM_MUSIC,
                            100
                    );
    
            // --------------------------------------------------------
            // Handlers
            // --------------------------------------------------------
    
            metronomeHandler =
                    new Handler(
                            Looper.getMainLooper()
                    );
    
            stepHandler =
                    new Handler(
                            Looper.getMainLooper()
                    );
    
            // --------------------------------------------------------
            // Audio Manager
            // --------------------------------------------------------
    
            audioManager =
                    (AudioManager)
                            getSystemService(
                                    Context.AUDIO_SERVICE
                            );
    
            // --------------------------------------------------------
            // Bluetooth Adapter
            // --------------------------------------------------------
    
            BluetoothManager bluetoothManager =
                    (BluetoothManager)
                            getSystemService(
                                    Context.BLUETOOTH_SERVICE
                            );
    
            if (bluetoothManager != null) {
    
                bluetoothAdapter =
                        bluetoothManager.getAdapter();
            }
    
            // --------------------------------------------------------
            // Bluetooth Receiver
            // --------------------------------------------------------
    
            IntentFilter bluetoothFilter =
                    new IntentFilter(
                            BluetoothAdapter.ACTION_STATE_CHANGED
                    );
    
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU) {
    
                registerReceiver(
                        bluetoothStateReceiver,
                        bluetoothFilter,
                        Context.RECEIVER_NOT_EXPORTED
                );
    
            } else {
    
                registerReceiver(
                        bluetoothStateReceiver,
                        bluetoothFilter
                );
            }
    
            // --------------------------------------------------------
            // BLE Status
            // --------------------------------------------------------
    
            updateBleStatus(
                    isThai
                            ? "📡 BLE: กำลังตรวจสอบ..."
                            : "📡 BLE: Checking..."
            );
    
            // --------------------------------------------------------
            // Buttons
            // --------------------------------------------------------
    
            setupButtons();
    
            // --------------------------------------------------------
            // Permission
            // --------------------------------------------------------
    
            requestBluetoothPermissions();
    
            // --------------------------------------------------------
            // UI
            // --------------------------------------------------------
    
            updateModeUI();
    
            updateLanguageUI();
            initFallDetection();
        }
    
        // ============================================================
        // SETUP BUTTONS
        // ============================================================
    
        private void setupButtons() {
    
            // --------------------------------------------------------
            // MENU
            // --------------------------------------------------------
    
            btnMenu.setOnClickListener(v ->
                    drawerLayout.openDrawer(
                            GravityCompat.START
                    )
            );
    
            // --------------------------------------------------------
            // SOS
            // --------------------------------------------------------
    
            btnAction.setOnClickListener(v -> {
    
                if (!isActionActive) {
    
                    startAction();
    
                } else {
    
                    stopActionWithReport();
                }
            });
    
            // --------------------------------------------------------
            // 1669
            // --------------------------------------------------------
    
            btnCall1669.setOnClickListener(v ->
                    showSimulationDialog(
    
                            isThai
                                    ? "📞 จำลองการโทร 1669"
                                    : "📞 Simulated call to 1669",
    
                            isThai
                                    ? "กำลังเชื่อมต่อไปยังศูนย์การแพทย์ฉุกเฉิน 1669...\n\n" +
                                      "[สถานะจำลอง]: เชื่อมต่อสำเร็จ"
    
                                    : "Connecting to emergency medical services at 1669...\n\n" +
                                      "[Simulation]: Connection successful."
                    )
            );
    
            // --------------------------------------------------------
            // GPS
            // --------------------------------------------------------
    
            btnGpsShare.setOnClickListener(v ->
                    showSimulationDialog(
    
                            isThai
                                    ? "📍 จำลองการแชร์พิกัด GPS"
                                    : "📍 Simulated GPS location sharing",
    
                            isThai
                                    ? "กำลังดึงพิกัดผ่านดาวเทียม...\n\n" +
                                      "[สถานะจำลอง]: ส่งพิกัดสำเร็จ\n" +
                                      "Latitude: 13.7563° N\n" +
                                      "Longitude: 100.5018° E"
    
                                    : "Retrieving your GPS location...\n\n" +
                                      "[Simulation]: Location shared successfully.\n" +
                                      "Latitude: 13.7563° N\n" +
                                      "Longitude: 100.5018° E"
                    )
            );
    
            // --------------------------------------------------------
            // FLASHLIGHT
            // --------------------------------------------------------
    
            btnFlashlight.setOnClickListener(v ->
                    Toast.makeText(
                            this,
                            isThai
                                    ? "🔦 จำลองเปิด/ปิด ไฟฉายฉุกเฉิน"
                                    : "🔦 Emergency flashlight simulated",
                            Toast.LENGTH_SHORT
                    ).show()
            );
    
            // --------------------------------------------------------
            // MEDICAL ID
            // --------------------------------------------------------
    
            btnMedicalId.setOnClickListener(v ->
                    showMedicalIdDialog()
            );
    
            // --------------------------------------------------------
            // LANGUAGE
            // --------------------------------------------------------
    
            btnLanguage.setOnClickListener(v ->
                    showLanguageDialog()
            );
    
            // --------------------------------------------------------
            // CPR
            // --------------------------------------------------------
    
            menuCpr.setOnClickListener(v -> {
    
                currentMode = "CPR";
    
                updateModeUI();
    
                drawerLayout.closeDrawers();
            });
    
            // --------------------------------------------------------
            // CHOKING
            // --------------------------------------------------------
    
            menuChoking.setOnClickListener(v -> {
    
                currentMode = "CHOKING";
    
                updateModeUI();
    
                drawerLayout.closeDrawers();
            });
    
            // --------------------------------------------------------
            // BLEEDING
            // --------------------------------------------------------
    
            menuBleeding.setOnClickListener(v -> {
    
                currentMode = "BLEEDING";
    
                updateModeUI();
    
                drawerLayout.closeDrawers();
            });
    
            // --------------------------------------------------------
            // SEIZURE
            // --------------------------------------------------------
    
            menuSeizure.setOnClickListener(v -> {
    
                currentMode = "SEIZURE";
    
                updateModeUI();
    
                drawerLayout.closeDrawers();
            });
        }
    
    // ============================================================
    // LANGUAGE DIALOG
    // ============================================================
    
        private void showLanguageDialog() {
    
            String[] languages = {
                    "🇹🇭 ไทย",
                    "🇬🇧 English"
            };
    
            int checkedItem = isThai ? 0 : 1;
    
            new AlertDialog.Builder(this)
                    .setTitle("🌐 " + (isThai ? "เลือกภาษา" : "Choose language"))
                    .setSingleChoiceItems(
                            languages,
                            checkedItem,
                            (dialog, which) -> {
    
                                if (which == 0) {
                                    isThai = true;
                                } else {
                                    isThai = false;
                                }
    
                                // บันทึกภาษาล่าสุดไว้ถาวร
                                getSharedPreferences("ResQPrefs", MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("***", isThai)
                                        .apply();
    
                                // หยุดเสียงเดิมก่อนเปลี่ยนภาษา
                                if (tts != null) {
                                    tts.stop();
                                }
    
                                setTtsLanguage();
                                updateLanguageUI();
                                updateModeUI();
    
                                dialog.dismiss();
    
                                Toast.makeText(
                                        MainActivity.this,
                                        isThai
                                                ? "🇹🇭 เปลี่ยนเป็นภาษาไทยแล้ว"
                                                : "🇬🇧 Language changed to English",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                    )
                    .setNegativeButton(
                            isThai ? "ยกเลิก" : "Cancel",
                            null
                    )
                    .show();
        }
    
        // ============================================================
        // UPDATE LANGUAGE UI
        // ============================================================
    
        private void updateLanguageUI() {
    
            if (btnLanguage != null) {
    
                btnLanguage.setText(
                        isThai
                                ? "🌐  ไทย / English"
                                : "🌐  English / ไทย"
                );
            }
    
            if (tvCurrentLanguage != null) {
    
                tvCurrentLanguage.setText(
                        isThai
                                ? "ภาษาปัจจุบัน: ไทย"
                                : "Current language: English"
                );
            }
    
            if (btnCall1669 != null) {
    
                btnCall1669.setText(
                        "📞 1669"
                );
            }
    
            if (btnGpsShare != null) {
    
                btnGpsShare.setText(
                        "📍 GPS"
                );
            }
    
            if (btnFlashlight != null) {
    
                btnFlashlight.setText(
                        isThai
                                ? "🔦 ไฟฉาย"
                                : "🔦 Flashlight"
                );
            }
    
            if (btnMedicalId != null) {
    
                btnMedicalId.setText(
                        isThai
                                ? "🩺"
                                : "🩺"
                );
            }
    
            if (menuCpr != null) {
    
                menuCpr.setText(
                        isThai
                                ? "❤️   CPR (ปั๊มหัวใจ)"
                                : "❤️   CPR"
                );
            }
    
            if (menuChoking != null) {
    
                menuChoking.setText(
                        isThai
                                ? "🗣️   สำลักอาหาร"
                                : "🗣️   Choking"
                );
            }
    
            if (menuBleeding != null) {
    
                menuBleeding.setText(
                        isThai
                                ? "🩸   ห้ามเลือด"
                                : "🩸   Severe Bleeding"
                );
            }
    
            if (menuSeizure != null) {
    
                menuSeizure.setText(
                        isThai
                                ? "⚡   อาการชัก"
                                : "⚡   Seizure"
                );
            }
        }
    
        // ============================================================
        // BLUETOOTH PERMISSION
        // ============================================================
    
        private void requestBluetoothPermissions() {
    
            if (bluetoothAdapter == null) {
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: เครื่องนี้ไม่มี Bluetooth"
                                : "❌ BLE: This device has no Bluetooth"
                );
    
                return;
            }
    
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S) {
    
                boolean scanGranted =
                        ActivityCompat.checkSelfPermission(
                                this,
                                Manifest.permission.BLUETOOTH_SCAN
                        ) ==
                                PackageManager.PERMISSION_GRANTED;
    
                boolean advertiseGranted =
                        ActivityCompat.checkSelfPermission(
                                this,
                                Manifest.permission.BLUETOOTH_ADVERTISE
                        ) ==
                                PackageManager.PERMISSION_GRANTED;
    
                boolean connectGranted =
                        ActivityCompat.checkSelfPermission(
                                this,
                                Manifest.permission.BLUETOOTH_CONNECT
                        ) ==
                                PackageManager.PERMISSION_GRANTED;
    
                if (!scanGranted ||
                        !advertiseGranted ||
                        !connectGranted) {
    
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_ADVERTISE,
                                    Manifest.permission.BLUETOOTH_CONNECT
                            },
                            PERMISSION_REQUEST_CODE
                    );
    
                } else {
    
                    checkBluetoothAndStartScanner();
                }
    
            } else {
    
                boolean locationGranted =
                        ActivityCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) ==
                                PackageManager.PERMISSION_GRANTED;
    
                if (!locationGranted) {
    
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            },
                            PERMISSION_REQUEST_CODE
                    );
    
                } else {
    
                    checkBluetoothAndStartScanner();
                }
            }
        }
    
        // ============================================================
        // CHECK BLUETOOTH
        // ============================================================
    
        @SuppressLint("MissingPermission")
        private void checkBluetoothAndStartScanner() {
    
            if (bluetoothAdapter == null) {
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: ไม่พบ Bluetooth Adapter"
                                : "❌ BLE: Bluetooth adapter not found"
                );
    
                return;
            }
    
            if (!bluetoothAdapter.isEnabled()) {
    
                updateBleStatus(
                        isThai
                                ? "⚠️ BLE: Bluetooth ปิดอยู่ — เปิด Bluetooth ได้เลย"
                                : "⚠️ BLE: Bluetooth is off — please turn it on"
                );
    
                return;
            }
    
            startBystanderScanner();
        }
    
        // ============================================================
        // PERMISSION CALLBACK
        // ============================================================
    
        @Override
        public void onRequestPermissionsResult(
                int requestCode,
                @NonNull String[] permissions,
                @NonNull int[] grantResults) {
    
            super.onRequestPermissionsResult(
                    requestCode,
                    permissions,
                    grantResults
            );
    
            if (requestCode !=
                    PERMISSION_REQUEST_CODE) {
    
                return;
            }
    
            boolean allGranted = true;
    
            for (int result : grantResults) {
    
                if (result !=
                        PackageManager.PERMISSION_GRANTED) {
    
                    allGranted = false;
    
                    break;
                }
            }
    
            if (allGranted) {
    
                Toast.makeText(
                        this,
                        isThai
                                ? "✅ อนุญาต Bluetooth แล้ว"
                                : "✅ Bluetooth permissions granted",
                        Toast.LENGTH_SHORT
                ).show();
    
                checkBluetoothAndStartScanner();
    
            } else {
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: ไม่ได้รับสิทธิ์ Bluetooth"
                                : "❌ BLE: Bluetooth permission denied"
                );
    
                Toast.makeText(
                        this,
                        isThai
                                ? "❌ กรุณาอนุญาต Bluetooth เพื่อรับสัญญาณฉุกเฉิน"
                                : "❌ Please allow Bluetooth permissions to receive emergency signals",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    
        // ============================================================
        // BLE SENDER
        // ============================================================
    
        @SuppressLint("MissingPermission")
        private void startOfflineBystanderBeacon() {
    
            if (bluetoothAdapter == null) {
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: เครื่องนี้ไม่มี Bluetooth"
                                : "❌ BLE: This device has no Bluetooth"
                );
    
                Toast.makeText(
                        this,
                        isThai
                                ? "❌ เครื่องนี้ไม่พบ Bluetooth"
                                : "❌ Bluetooth not found",
                        Toast.LENGTH_LONG
                ).show();
    
                return;
            }
    
            if (!bluetoothAdapter.isEnabled()) {
    
                updateBleStatus(
                        isThai
                                ? "⚠️ BLE: กรุณาเปิด Bluetooth ก่อนส่ง"
                                : "⚠️ BLE: Please turn on Bluetooth before broadcasting"
                );
    
                Toast.makeText(
                        this,
                        isThai
                                ? "⚠️ กรุณาเปิด Bluetooth ก่อน"
                                : "⚠️ Please turn on Bluetooth first",
                        Toast.LENGTH_LONG
                ).show();
    
                return;
            }
    
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S) {
    
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                ) !=
                        PackageManager.PERMISSION_GRANTED) {
    
                    updateBleStatus(
                            isThai
                                    ? "❌ BLE: ไม่มีสิทธิ์ ADVERTISE"
                                    : "❌ BLE: ADVERTISE permission missing"
                    );
    
                    return;
                }
            }
    
            if (!bluetoothAdapter
                    .isMultipleAdvertisementSupported()) {
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: เครื่องไม่รองรับ Advertising"
                                : "❌ BLE: This device does not support advertising"
                );
    
                Toast.makeText(
                        this,
                        isThai
                                ? "❌ มือถือรุ่นนี้ไม่รองรับ BLE Advertising"
                                : "❌ This phone does not support BLE advertising",
                        Toast.LENGTH_LONG
                ).show();
    
                return;
            }
    
            bluetoothLeAdvertiser =
                    bluetoothAdapter
                            .getBluetoothLeAdvertiser();
    
            if (bluetoothLeAdvertiser == null) {
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: Advertiser ใช้งานไม่ได้"
                                : "❌ BLE: Advertiser unavailable"
                );
    
                return;
            }
    
            if (isBleAdvertising) {
    
                updateBleStatus(
                        isThai
                                ? "📡 BLE: กำลังส่งอยู่แล้ว"
                                : "📡 BLE: Already broadcasting"
                );
    
                return;
            }
    
            /*
             * Payload ต้องเหมือนเดิม
             * เพื่อให้อุปกรณ์รับเข้าใจ protocol เดิม
             */
    
            String payload =
                    "RESQ_" + currentMode;
    
            AdvertiseSettings settings =
                    new AdvertiseSettings.Builder()
    
                            .setAdvertiseMode(
                                    AdvertiseSettings
                                            .ADVERTISE_MODE_LOW_LATENCY
                            )
    
                            .setTxPowerLevel(
                                    AdvertiseSettings
                                            .ADVERTISE_TX_POWER_HIGH
                            )
    
                            .setConnectable(false)
    
                            .build();
    
            AdvertiseData data =
                    new AdvertiseData.Builder()
    
                            .setIncludeDeviceName(false)
    
                            .addManufacturerData(
                                    RESQ_MANUFACTURER_ID,
                                    payload.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
    
                            .build();
    
            advertiseCallback =
                    new AdvertiseCallback() {
    
                        @Override
                        public void onStartSuccess(
                                AdvertiseSettings settingsInEffect) {
    
                            super.onStartSuccess(
                                    settingsInEffect
                            );
    
                            isBleAdvertising = true;
    
                            updateBleStatus(
                                    isThai
                                            ? "📡 BLE: กำลังส่ง " + payload
                                            : "📡 BLE: Broadcasting " + payload
                            );
    
                            Toast.makeText(
                                    MainActivity.this,
                                    isThai
                                            ? "📡 เริ่มส่ง " + payload + " แล้ว"
                                            : "📡 Started broadcasting " + payload,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
    
                        @Override
                        public void onStartFailure(
                                int errorCode) {
    
                            super.onStartFailure(
                                    errorCode
                            );
    
                            isBleAdvertising = false;
    
                            updateBleStatus(
                                    isThai
                                            ? "❌ BLE ส่งไม่สำเร็จ Error: " + errorCode
                                            : "❌ BLE broadcast failed. Error: " + errorCode
                            );
                        }
                    };
    
            try {
    
                bluetoothLeAdvertiser
                        .startAdvertising(
                                settings,
                                data,
                                advertiseCallback
                        );
    
            } catch (Exception e) {
    
                isBleAdvertising = false;
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: เกิดข้อผิดพลาดในการส่ง"
                                : "❌ BLE: Broadcasting error"
                );
            }
        }
    
        // ============================================================
        // STOP BLE SENDER
        // ============================================================
    
        @SuppressLint("MissingPermission")
        private void stopOfflineBystanderBeacon() {
    
            if (!isBleAdvertising ||
                    bluetoothLeAdvertiser == null ||
                    advertiseCallback == null) {
    
                return;
            }
    
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S) {
    
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                ) !=
                        PackageManager.PERMISSION_GRANTED) {
    
                    return;
                }
            }
    
            try {
    
                bluetoothLeAdvertiser
                        .stopAdvertising(
                                advertiseCallback
                        );
    
            } catch (Exception ignored) {
            }
    
            isBleAdvertising = false;
        }
    
        // ============================================================
        // BLE SCANNER
        // ============================================================
    
        @SuppressLint("MissingPermission")
        private void startBystanderScanner() {
    
            if (bluetoothAdapter == null) {
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: ไม่พบ Bluetooth"
                                : "❌ BLE: Bluetooth not found"
                );
    
                return;
            }
    
            if (!bluetoothAdapter.isEnabled()) {
    
                updateBleStatus(
                        isThai
                                ? "⚠️ BLE: Bluetooth ปิดอยู่"
                                : "⚠️ BLE: Bluetooth is off"
                );
    
                return;
            }
    
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S) {
    
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                ) !=
                        PackageManager.PERMISSION_GRANTED) {
    
                    updateBleStatus(
                            isThai
                                    ? "❌ BLE: ไม่มีสิทธิ์ SCAN"
                                    : "❌ BLE: SCAN permission missing"
                    );
    
                    return;
                }
            }
    
            bluetoothLeScanner =
                    bluetoothAdapter
                            .getBluetoothLeScanner();
    
            if (bluetoothLeScanner == null) {
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: Scanner ใช้งานไม่ได้"
                                : "❌ BLE: Scanner unavailable"
                );
    
                return;
            }
    
            if (isScanning) {
    
                updateBleStatus(
                        isThai
                                ? "📡 BLE: กำลังสแกนอยู่แล้ว"
                                : "📡 BLE: Already scanning"
                );
    
                return;
            }
    
            scanCallback =
                    new ScanCallback() {
    
                        @Override
                        public void onScanResult(
                                int callbackType,
                                ScanResult result) {
    
                            super.onScanResult(
                                    callbackType,
                                    result
                            );
    
                            if (result == null ||
                                    result.getScanRecord() == null) {
    
                                return;
                            }
    
                            byte[] manufacturerData =
                                    result.getScanRecord()
                                            .getManufacturerSpecificData(
                                                    RESQ_MANUFACTURER_ID
                                            );
    
                            if (manufacturerData == null ||
                                    manufacturerData.length == 0) {
    
                                return;
                            }
    
                            String beaconMessage;
    
                            try {
    
                                beaconMessage =
                                        new String(
                                                manufacturerData,
                                                StandardCharsets.UTF_8
                                        );
    
                            } catch (Exception e) {
    
                                return;
                            }
    
                            if (!beaconMessage.startsWith(
                                    "RESQ_"
                            )) {
    
                                return;
                            }
    
                            int rssi =
                                    result.getRssi();
    
                            updateBleStatus(
                                    isThai
                                            ? "🚨 BLE: รับ " +
                                              beaconMessage +
                                              " | RSSI: " +
                                              rssi +
                                              " dBm"
    
                                            : "🚨 BLE: Received " +
                                              beaconMessage +
                                              " | RSSI: " +
                                              rssi +
                                              " dBm"
                            );
    
                            if (!hasAlertedForThisEvent) {
    
                                hasAlertedForThisEvent = true;
    
                                triggerBystanderAlert(
                                        beaconMessage,
                                        rssi
                                );
                            }
                        }
    
                        @Override
                        public void onScanFailed(
                                int errorCode) {
    
                            super.onScanFailed(
                                    errorCode
                            );
    
                            isScanning = false;
    
                            updateBleStatus(
                                    "❌ BLE Scan Error: " +
                                            errorCode
                            );
                        }
                    };
    
            ScanSettings settings =
                    new ScanSettings.Builder()
    
                            .setScanMode(
                                    ScanSettings
                                            .SCAN_MODE_LOW_LATENCY
                            )
    
                            .build();
    
            try {
    
                bluetoothLeScanner.startScan(
                        null,
                        settings,
                        scanCallback
                );
    
                isScanning = true;
    
                updateBleStatus(
                        isThai
                                ? "📡 BLE: กำลังสแกนหาสัญญาณฉุกเฉิน..."
                                : "📡 BLE: Scanning for emergency signals..."
                );
    
            } catch (Exception e) {
    
                isScanning = false;
    
                updateBleStatus(
                        isThai
                                ? "❌ BLE: เริ่ม Scanner ไม่สำเร็จ"
                                : "❌ BLE: Failed to start scanner"
                );
            }
        }
    
        // ============================================================
        // STOP BLE SCANNER
        // ============================================================
    
        @SuppressLint("MissingPermission")
        private void stopBystanderScanner() {
    
            if (!isScanning ||
                    bluetoothLeScanner == null ||
                    scanCallback == null) {
    
                return;
            }
    
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S) {
    
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                ) !=
                        PackageManager.PERMISSION_GRANTED) {
    
                    return;
                }
            }
    
            try {
    
                bluetoothLeScanner.stopScan(
                        scanCallback
                );
    
            } catch (Exception ignored) {
            }
    
            isScanning = false;
        }
    
        // ============================================================
        // BLE STATUS
        // ============================================================
    
        private void updateBleStatus(
                String message) {
    
            runOnUiThread(() -> {
    
                if (tvBleStatus == null) {
    
                    return;
                }
    
                tvBleStatus.setText(
                        message
                );
    
                if (message.startsWith("❌")) {
    
                    tvBleStatus.setTextColor(
                            Color.parseColor(
                                    "#B91C1C"
                            )
                    );
    
                } else if (message.startsWith("🚨")) {
    
                    tvBleStatus.setTextColor(
                            Color.parseColor(
                                    "#B91C1C"
                            )
                    );
    
                } else if (message.startsWith("📡") ||
                        message.startsWith("🟢")) {
    
                    tvBleStatus.setTextColor(
                            Color.parseColor(
                                    "#166534"
                            )
                    );
    
                } else {
    
                    tvBleStatus.setTextColor(
                            Color.parseColor(
                                    "#475569"
                            )
                    );
                }
            });
        }
    
        // ============================================================
        // BYSTANDER ALERT
        // ============================================================
    
        private void triggerBystanderAlert(
                String mode,
                int rssi) {
    
            runOnUiThread(() -> {
    
                Vibrator vibrator =
                        (Vibrator)
                                getSystemService(
                                        Context.VIBRATOR_SERVICE
                                );
    
                if (vibrator != null) {
    
                    if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.O) {
    
                        vibrator.vibrate(
                                VibrationEffect.createWaveform(
                                        new long[]{
                                                0,
                                                500,
                                                200,
                                                500,
                                                200,
                                                700
                                        },
                                        -1
                                )
                        );
    
                    } else {
    
                        vibrator.vibrate(
                                1500
                        );
                    }
                }
    
                if (toneGen != null) {
    
                    toneGen.startTone(
                            ToneGenerator
                                    .TONE_CDMA_ALERT_CALL_GUARD,
                            1200
                    );
                }
    
                String emergencyType;
    
                if (mode.equals("RESQ_CPR")) {
    
                    emergencyType =
                            isThai
                                    ? "CPR (ปั๊มหัวใจ)"
                                    : "CPR";
    
                } else if (mode.equals(
                        "RESQ_CHOKING")) {
    
                    emergencyType =
                            isThai
                                    ? "สำลักอาหาร"
                                    : "Choking";
    
                } else if (mode.equals(
                        "RESQ_BLEEDING")) {
    
                    emergencyType =
                            isThai
                                    ? "ห้ามเลือด"
                                    : "Severe Bleeding";
    
                } else if (mode.equals(
                        "RESQ_SEIZURE")) {
    
                    emergencyType =
                            isThai
                                    ? "อาการชัก"
                                    : "Seizure";
    
                } else {
    
                    emergencyType =
                            mode;
                }
    
                String title =
                        isThai
                                ? "⚠️ มีคนต้องการความช่วยเหลือด่วน!"
                                : "⚠️ Someone needs urgent help!";
    
                String message =
                        isThai
    
                                ? "ประเภทเหตุการณ์: " +
                                  emergencyType +
                                  "\n\n" +
                                  "📡 รับสัญญาณผ่าน Bluetooth BLE สำเร็จ" +
                                  "\n" +
                                  "📶 RSSI: " +
                                  rssi +
                                  " dBm" +
                                  "\n\n" +
                                  "กรุณาเข้าไปช่วยเหลืออย่างปลอดภัย"
    
                                : "Emergency type: " +
                                  emergencyType +
                                  "\n\n" +
                                  "📡 Emergency signal received via Bluetooth BLE." +
                                  "\n" +
                                  "📶 RSSI: " +
                                  rssi +
                                  " dBm" +
                                  "\n\n" +
                                  "Please approach and help only if it is safe to do so.";
    
                new AlertDialog.Builder(
                        MainActivity.this
                )
    
                        .setTitle(title)
    
                        .setMessage(message)
    
                        .setPositiveButton(
                                isThai
                                        ? "รับทราบ / ไปช่วยเหลือ"
                                        : "Acknowledge / Help",
    
                                (dialog, which) -> {
    
                                    dialog.dismiss();
    
                                    hasAlertedForThisEvent =
                                            false;
    
                                    updateBleStatus(
                                            isThai
                                                    ? "📡 BLE: กำลังสแกนต่อ..."
                                                    : "📡 BLE: Scanning continues..."
                                    );
                                }
                        )
    
                        .setCancelable(false)
    
                        .show();
            });
        }
    
        // ============================================================
        // SIMULATION DIALOG
        // ============================================================
    
        private void showSimulationDialog(
                String title,
                String message) {
    
            new AlertDialog.Builder(this)
    
                    .setTitle(title)
    
                    .setMessage(message)
    
                    .setPositiveButton(
                            isThai
                                    ? "ตกลง"
                                    : "OK",
    
                            (dialog, which) ->
                                    dialog.dismiss()
                    )
    
                    .show();
        }
    
        // ============================================================
        // MEDICAL ID
        // ============================================================
    
        private void showMedicalIdDialog() {
    
            String title =
                    isThai
                            ? "🩺 บัตรประจำตัวผู้ป่วย (Medical ID)"
                            : "🩺 Medical ID";
    
            String message =
                    isThai
    
                            ? "ชื่อ-นามสกุล: คุณออม (Aom)\n" +
                              "อายุ: 18 ปี | กรุ๊ปเลือด: O+\n\n" +
                              "⚠️ โรคประจำตัว / ภูมิแพ้:\n" +
                              "- ไม่มี\n\n" +
                              "📞 ผู้ติดต่อฉุกเฉิน:\n" +
                              "- มารดา : 091-XXX-XXXX"
    
                            : "Name: Aom\n" +
                              "Age: 18 | Blood type: O+\n\n" +
                              "⚠️ Medical conditions / Allergies:\n" +
                              "- None\n\n" +
                              "📞 Emergency contact:\n" +
                              "- Mother : 091-XXX-XXXX";
    
            new AlertDialog.Builder(this)
    
                    .setTitle(title)
    
                    .setMessage(message)
    
                    .setPositiveButton(
                            isThai
                                    ? "ปิด"
                                    : "Close",
    
                            (dialog, which) ->
                                    dialog.dismiss()
                    )
    
                    .show();
        }
    
        // ============================================================
        // UPDATE MODE UI
        // ============================================================
    
        private void updateModeUI() {
    
            if (isActionActive) {
    
                stopAction();
            }
    
            cprStep = 0;
    
            btnAction.setBackgroundTintList(
                    ColorStateList.valueOf(
                            COLOR_RED
                    )
            );
    
            updateMenuHighlight();
    
            // ========================================================
            // CPR
            // ========================================================
    
            if (currentMode.equals("CPR")) {
    
                if (isThai) {
    
                    tvCurrentTopic.setText(
                            "สถานการณ์: CPR (ปั๊มหัวใจ)"
                    );
    
                    tvInstruction.setText(
                            "กดปุ่ม SOS เพื่อเริ่มระบบเสียงนำทาง CPR และกระจายสัญญาณฉุกเฉิน (BLE Beacon)"
                    );
    
                    btnAction.setText(
                            "SOS\nเริ่ม CPR"
                    );
    
                } else {
    
                    tvCurrentTopic.setText(
                            "Emergency: CPR"
                    );
    
                    tvInstruction.setText(
                            "Press SOS to start CPR voice guidance and broadcast an emergency BLE beacon."
                    );
    
                    btnAction.setText(
                            "SOS\nStart CPR"
                    );
                }
    
                imgGuide.setImageResource(
                        R.drawable.guide_cpr
                );
    
                // ========================================================
                // CHOKING
                // ========================================================
    
            } else if (currentMode.equals(
                    "CHOKING")) {
    
                if (isThai) {
    
                    tvCurrentTopic.setText(
                            "สถานการณ์: สำลักอาหาร (Choking)"
                    );
    
                    tvInstruction.setText(
                            "กดปุ่ม SOS เพื่อเริ่มวิธีช่วยเหลือคนสำลัก"
                    );
    
                    btnAction.setText(
                            "SOS\nเริ่มช่วยเหลือ"
                    );
    
                } else {
    
                    tvCurrentTopic.setText(
                            "Emergency: Choking"
                    );
    
                    tvInstruction.setText(
                            "Press SOS to start voice guidance for helping someone who is choking."
                    );
    
                    btnAction.setText(
                            "SOS\nStart Help"
                    );
                }
    
                imgGuide.setImageResource(
                        R.drawable.guide_choking
                );
    
                // ========================================================
                // BLEEDING
                // ========================================================
    
            } else if (currentMode.equals(
                    "BLEEDING")) {
    
                if (isThai) {
    
                    tvCurrentTopic.setText(
                            "สถานการณ์: ห้ามเลือด (Bleeding)"
                    );
    
                    tvInstruction.setText(
                            "กดปุ่ม SOS เพื่อเริ่มวิธีปฐมพยาบาลห้ามเลือด"
                    );
    
                    btnAction.setText(
                            "SOS\nเริ่มห้ามเลือด"
                    );
    
                } else {
    
                    tvCurrentTopic.setText(
                            "Emergency: Severe Bleeding"
                    );
    
                    tvInstruction.setText(
                            "Press SOS to start voice guidance for controlling severe bleeding."
                    );
    
                    btnAction.setText(
                            "SOS\nStart Help"
                    );
                }
    
                imgGuide.setImageResource(
                        R.drawable.guide_bleeding
                );
    
                // ========================================================
                // SEIZURE
                // ========================================================
    
            } else if (currentMode.equals(
                    "SEIZURE")) {
    
                if (isThai) {
    
                    tvCurrentTopic.setText(
                            "สถานการณ์: อาการชัก (Seizure)"
                    );
    
                    tvInstruction.setText(
                            "กดปุ่ม SOS เพื่อเริ่มวิธีปฏิบัติเมื่อพบผู้ป่วยชัก"
                    );
    
                    btnAction.setText(
                            "SOS\nเริ่มแนะนำ"
                    );
    
                } else {
    
                    tvCurrentTopic.setText(
                            "Emergency: Seizure"
                    );
    
                    tvInstruction.setText(
                            "Press SOS to start voice guidance for helping someone who is having a seizure."
                    );
    
                    btnAction.setText(
                            "SOS\nStart Guidance"
                    );
                }
    
                imgGuide.setImageResource(
                        R.drawable.guide_seizure
                );
            }
    
            updateLanguageUI();
        }
    
        // ============================================================
        // MENU HIGHLIGHT
        // ============================================================
    
        private void updateMenuHighlight() {
    
            int defaultBg =
                    Color.TRANSPARENT;
    
            int defaultText =
                    Color.parseColor(
                            "#334155"
                    );
    
            int activeBg =
                    Color.parseColor(
                            "#FEE2E2"
                    );
    
            int activeText =
                    Color.parseColor(
                            "#B91C1C"
                    );
    
            menuCpr.setBackgroundTintList(
                    ColorStateList.valueOf(
                            defaultBg
                    )
            );
    
            menuCpr.setTextColor(
                    defaultText
            );
    
            menuChoking.setBackgroundTintList(
                    ColorStateList.valueOf(
                            defaultBg
                    )
            );
    
            menuChoking.setTextColor(
                    defaultText
            );
    
            menuBleeding.setBackgroundTintList(
                    ColorStateList.valueOf(
                            defaultBg
                    )
            );
    
            menuBleeding.setTextColor(
                    defaultText
            );
    
            menuSeizure.setBackgroundTintList(
                    ColorStateList.valueOf(
                            defaultBg
                    )
            );
    
            menuSeizure.setTextColor(
                    defaultText
            );
    
            switch (currentMode) {
    
                case "CPR":
    
                    menuCpr.setBackgroundTintList(
                            ColorStateList.valueOf(
                                    activeBg
                            )
                    );
    
                    menuCpr.setTextColor(
                            activeText
                    );
    
                    break;
    
                case "CHOKING":
    
                    menuChoking.setBackgroundTintList(
                            ColorStateList.valueOf(
                                    activeBg
                            )
                    );
    
                    menuChoking.setTextColor(
                            activeText
                    );
    
                    break;
    
                case "BLEEDING":
    
                    menuBleeding.setBackgroundTintList(
                            ColorStateList.valueOf(
                                    activeBg
                            )
                    );
    
                    menuBleeding.setTextColor(
                            activeText
                    );
    
                    break;
    
                case "SEIZURE":
    
                    menuSeizure.setBackgroundTintList(
                            ColorStateList.valueOf(
                                    activeBg
                            )
                    );
    
                    menuSeizure.setTextColor(
                            activeText
                    );
    
                    break;
            }
        }
    
        // ============================================================
        // START ACTION
        // ============================================================
    
        private void startAction() {
    
            isActionActive = true;
    
            startTimeMillis =
                    System.currentTimeMillis();
    
            if (audioManager != null) {
    
                audioManager.setMode(
                        AudioManager.MODE_NORMAL
                );
    
                audioManager.setSpeakerphoneOn(
                        true
                );
            }
    
            btnAction.setText(
                    isThai
                            ? "หยุด / สรุปเคส"
                            : "Stop / Summary"
            );
    
            btnAction.setBackgroundTintList(
                    ColorStateList.valueOf(
                            COLOR_GRAY
                    )
            );
    
            // --------------------------------------------------------
            // BLE Sender
            // --------------------------------------------------------
    
            startOfflineBystanderBeacon();
    
            // ========================================================
            // แสดงภาพตามสถานะการณ์ต่างๆ
            // ========================================================
    
            if (currentMode.equals("CPR")) {
    
                startCpr3Steps();
    
            } else if (currentMode.equals("CHOKING")) {
    
                imgGuide.setImageResource(
                        R.drawable.guide_choking_active
                );
    
                guidanceStep = 1;
                showChokingStep(1);
    
            } else if (currentMode.equals("BLEEDING")) {
    
                imgGuide.setImageResource(
                        R.drawable.guide_bleeding_active
                );
    
                guidanceStep = 1;
                showBleedingStep(1);
            } else if (currentMode.equals("SEIZURE")) {
    
                imgGuide.setImageResource(
                        R.drawable.guide_seizure_active
                );
    
                guidanceStep = 1;
                showSeizureStep(1);
            }
        }
    
        // ============================================================
        // CHOKING GUIDANCE
        // ============================================================
    
        private void showChokingStep(int step) {
    
            if (!isActionActive || !currentMode.equals("CHOKING")) {
                return;
            }
    
            guidanceStep = step;
    
            String screenText;
            String speechText;
    
            if (isThai) {
                switch (step) {
                    case 1:
                        screenText = "🔊 [ขั้นตอนที่ 1/4] ประเมินอาการ: หากผู้ป่วยยังไอหรือพูดได้ ให้กระตุ้นให้ไอต่อไป และเฝ้าดูอาการอย่างใกล้ชิด";
                        speechText = "ขั้นตอนที่หนึ่ง ประเมินอาการก่อน หากผู้ป่วยยังไอหรือพูดได้ ให้กระตุ้นให้ไอต่อไป และเฝ้าดูอาการอย่างใกล้ชิด";
                        break;
                    case 2:
                        screenText = "🔊 [ขั้นตอนที่ 2/4] ขอความช่วยเหลือ: หากผู้ป่วยหายใจไม่ได้หรืออาการรุนแรงขึ้น ให้เรียกคนช่วยและติดต่อบริการฉุกเฉินทันที";
                        speechText = "ขั้นตอนที่สอง ขอความช่วยเหลือ หากผู้ป่วยหายใจไม่ได้หรืออาการรุนแรงขึ้น ให้เรียกคนช่วยและติดต่อบริการฉุกเฉินทันที";
                        break;
                    case 3:
                        screenText = "🔊 [ขั้นตอนที่ 3/4] ช่วยเหลือตามความเหมาะสม: ปฏิบัติตามขั้นตอนปฐมพยาบาลสำหรับผู้ที่สำลัก โดยคำนึงถึงความปลอดภัยของผู้ป่วยและผู้ช่วยเหลือ";
                        speechText = "ขั้นตอนที่สาม ให้การช่วยเหลือตามความเหมาะสมสำหรับผู้ที่สำลัก โดยคำนึงถึงความปลอดภัยของผู้ป่วยและผู้ช่วยเหลือ";
                        break;
                    default:
                        screenText = "🔊 [ขั้นตอนที่ 4/4] เฝ้าระวัง: หากผู้ป่วยหมดสติหรือไม่หายใจตามปกติ ให้ขอความช่วยเหลือฉุกเฉินและเตรียมปฏิบัติตามคำแนะนำฉุกเฉิน";
                        speechText = "ขั้นตอนที่สี่ เฝ้าระวังอาการ หากผู้ป่วยหมดสติหรือไม่หายใจตามปกติ ให้ขอความช่วยเหลือฉุกเฉินและปฏิบัติตามคำแนะนำฉุกเฉิน";
                        break;
                }
            } else {
                switch (step) {
                    case 1:
                        screenText = "🔊 [Step 1/4] Assess: If the person can still cough or speak, encourage them to keep coughing and watch them closely.";
                        speechText = "Step one. Assess the person. If they can still cough or speak, encourage them to keep coughing and watch them closely.";
                        break;
                    case 2:
                        screenText = "🔊 [Step 2/4] Get help: If the person cannot breathe or their condition is getting worse, call for help and contact emergency services immediately.";
                        speechText = "Step two. Get help. If the person cannot breathe or their condition is getting worse, call for help and contact emergency services immediately.";
                        break;
                    case 3:
                        screenText = "🔊 [Step 3/4] Provide appropriate first aid: Follow appropriate choking first-aid guidance while keeping both the person and yourself safe.";
                        speechText = "Step three. Provide appropriate first aid for choking while keeping both the person and yourself safe.";
                        break;
                    default:
                        screenText = "🔊 [Step 4/4] Monitor closely: If the person becomes unresponsive or is not breathing normally, call emergency services and follow emergency instructions.";
                        speechText = "Step four. Monitor the person closely. If they become unresponsive or are not breathing normally, call emergency services and follow emergency instructions.";
                        break;
                }
            }
    
            tvInstruction.setText(screenText);
            speakTextWithId(speechText, "CHOKING_STEP_" + step);
        }
    
        private void advanceChokingGuidance() {
            if (guidanceStep < 4) {
                showChokingStep(guidanceStep + 1);
            }
        }
    
        // ============================================================
        // BLEEDING GUIDANCE
        // ============================================================
    
        private void showBleedingStep(int step) {
    
            if (!isActionActive || !currentMode.equals("BLEEDING")) {
                return;
            }
    
            guidanceStep = step;
    
            String screenText;
            String speechText;
    
            if (isThai) {
                switch (step) {
                    case 1:
                        screenText = "🔊 [ขั้นตอนที่ 1/4] ประเมินความรุนแรง: ตรวจดูว่ามีเลือดออกมากหรือไม่ และขอความช่วยเหลือฉุกเฉินเมื่อจำเป็น";
                        speechText = "ขั้นตอนที่หนึ่ง ประเมินความรุนแรงของเลือดที่ออก หากมีเลือดออกมากหรืออาการรุนแรง ให้ขอความช่วยเหลือฉุกเฉิน";
                        break;
                    case 2:
                        screenText = "🔊 [ขั้นตอนที่ 2/4] กดแผลโดยตรง: ใช้ผ้าสะอาดหรือวัสดุที่เหมาะสมกดลงบนบาดแผลโดยตรงอย่างต่อเนื่อง";
                        speechText = "ขั้นตอนที่สอง ใช้ผ้าสะอาดหรือวัสดุที่เหมาะสมกดลงบนบาดแผลโดยตรง และกดอย่างต่อเนื่อง";
                        break;
                    case 3:
                        screenText = "🔊 [ขั้นตอนที่ 3/4] รักษาแรงกด: หากเลือดยังออก ให้คงแรงกดไว้และหลีกเลี่ยงการเปิดผ้าดูแผลบ่อย ๆ";
                        speechText = "ขั้นตอนที่สาม รักษาแรงกดไว้ หากเลือดยังออก ให้กดต่อเนื่องและหลีกเลี่ยงการเปิดผ้าดูแผลบ่อย ๆ";
                        break;
                    default:
                        screenText = "🔊 [ขั้นตอนที่ 4/4] รอความช่วยเหลือ: เฝ้าดูอาการของผู้ป่วยและส่งต่อให้บุคลากรทางการแพทย์เมื่อมาถึง";
                        speechText = "ขั้นตอนที่สี่ รอความช่วยเหลือ เฝ้าดูอาการของผู้ป่วย และส่งต่อให้บุคลากรทางการแพทย์เมื่อมาถึง";
                        break;
                }
            } else {
                switch (step) {
                    case 1:
                        screenText = "🔊 [Step 1/4] Assess the bleeding: Check how severe the bleeding is and seek emergency help when needed.";
                        speechText = "Step one. Assess the bleeding. Check how severe it is and seek emergency help when needed.";
                        break;
                    case 2:
                        screenText = "🔊 [Step 2/4] Apply direct pressure: Use a clean cloth or suitable material and press directly on the wound.";
                        speechText = "Step two. Apply direct pressure. Use a clean cloth or suitable material and press directly on the wound.";
                        break;
                    case 3:
                        screenText = "🔊 [Step 3/4] Maintain pressure: Keep steady pressure on the wound. Avoid repeatedly lifting the cloth to check the wound.";
                        speechText = "Step three. Maintain steady pressure. Keep pressing on the wound and avoid repeatedly lifting the cloth to check it.";
                        break;
                    default:
                        screenText = "🔊 [Step 4/4] Wait for help: Monitor the person and hand over care to medical professionals when they arrive.";
                        speechText = "Step four. Wait for help. Monitor the person and hand over care to medical professionals when they arrive.";
                        break;
                }
            }
    
            tvInstruction.setText(screenText);
            speakTextWithId(speechText, "BLEEDING_STEP_" + step);
        }
    
        private void advanceBleedingGuidance() {
            if (guidanceStep < 4) {
                showBleedingStep(guidanceStep + 1);
            }
        }
    
        // ============================================================
        // SEIZURE GUIDANCE
        // ============================================================
    
        private void showSeizureStep(int step) {
    
            if (!isActionActive || !currentMode.equals("SEIZURE")) {
                return;
            }
    
            guidanceStep = step;
    
            String screenText;
            String speechText;
    
            if (isThai) {
                switch (step) {
                    case 1:
                        screenText = "🔊 [ขั้นตอนที่ 1/4] ป้องกันอันตราย: เคลื่อนย้ายสิ่งของที่อาจทำให้ผู้ป่วยบาดเจ็บ และอย่าพยายามยับยั้งการเคลื่อนไหวของผู้ป่วย";
                        speechText = "ขั้นตอนที่หนึ่ง ป้องกันอันตราย เคลื่อนย้ายสิ่งของที่อาจทำให้ผู้ป่วยบาดเจ็บ และอย่าพยายามยับยั้งการเคลื่อนไหวของผู้ป่วย";
                        break;
                    case 2:
                        screenText = "🔊 [ขั้นตอนที่ 2/4] รองรับศีรษะ: ใช้สิ่งที่นุ่มและปลอดภัยรองรับศีรษะ และดูแลพื้นที่รอบตัวให้โล่ง";
                        speechText = "ขั้นตอนที่สอง รองรับศีรษะ ใช้สิ่งที่นุ่มและปลอดภัยรองรับศีรษะ และดูแลพื้นที่รอบตัวให้โล่ง";
                        break;
                    case 3:
                        screenText = "🔊 [ขั้นตอนที่ 3/4] ห้ามทำสิ่งต่อไปนี้: อย่านำสิ่งของใส่ปาก และอย่าพยายามป้อนอาหาร น้ำ หรือยาในระหว่างที่กำลังชัก";
                        speechText = "ขั้นตอนที่สาม ห้ามนำสิ่งของใส่ปาก และอย่าพยายามป้อนอาหาร น้ำ หรือยาในระหว่างที่กำลังชัก";
                        break;
                    default:
                        screenText = "🔊 [ขั้นตอนที่ 4/4] หลังการชัก: เมื่อการชักหยุดลง ให้ตรวจการหายใจ จัดท่าให้ปลอดภัย และขอความช่วยเหลือฉุกเฉินหากหายใจผิดปกติ";
                        speechText = "ขั้นตอนที่สี่ หลังการชัก เมื่อการชักหยุดลง ให้ตรวจการหายใจ จัดท่าให้ปลอดภัย และขอความช่วยเหลือฉุกเฉินหากหายใจผิดปกติ";
                        break;
                }
            } else {
                switch (step) {
                    case 1:
                        screenText = "🔊 [Step 1/4] Protect from injury: Move nearby hazards away and do not try to restrain the person's movements.";
                        speechText = "Step one. Protect the person from injury. Move nearby hazards away and do not try to restrain their movements.";
                        break;
                    case 2:
                        screenText = "🔊 [Step 2/4] Protect the head: Cushion the person's head with something soft and keep the surrounding area clear.";
                        speechText = "Step two. Protect the head. Cushion the person's head with something soft and keep the surrounding area clear.";
                        break;
                    case 3:
                        screenText = "🔊 [Step 3/4] Do not do these things: Do not put anything in the person's mouth, and do not give food, water, or medicine during the seizure.";
                        speechText = "Step three. Do not put anything in the person's mouth, and do not give food, water, or medicine during the seizure.";
                        break;
                    default:
                        screenText = "🔊 [Step 4/4] After the seizure: When the seizure stops, check their breathing, place them in a safe position, and seek emergency help if their breathing is abnormal.";
                        speechText = "Step four. After the seizure, check their breathing, place them in a safe position, and seek emergency help if their breathing is abnormal.";
                        break;
                }
            }
    
            tvInstruction.setText(screenText);
            speakTextWithId(speechText, "SEIZURE_STEP_" + step);
        }
    
        private void advanceSeizureGuidance() {
            if (guidanceStep < 4) {
                showSeizureStep(guidanceStep + 1);
            }
        }
    
        // ============================================================
        // CPR - STEP 1
        // ============================================================
    
        private void startCpr3Steps() {
    
            cprStep = 1;
    
            imgGuide.setImageResource(
                    R.drawable.guide_cpr
            );
    
            if (isThai) {
    
                tvInstruction.setText(
                        "🔊 [ขั้นตอนที่ 1/3] เตรียมตัวผู้ป่วย: จัดให้นอนราบบนพื้นแข็ง และตรวจสอบการหายใจ"
                );
    
                speakTextWithId(
                        "ขั้นตอนที่หนึ่ง จัดผู้ป่วยนอนราบบนพื้นแข็ง และตรวจสอบการหายใจ",
                        "CPR_STEP_1"
                );
    
            } else {
    
                tvInstruction.setText(
                        "🔊 [Step 1/3] Prepare: Lay the person flat on a firm surface and check for normal breathing."
                );
    
                speakTextWithId(
                        "Step one. Lay the person flat on a firm surface and check for normal breathing.",
                        "CPR_STEP_1"
                );
            }
        }
    
        // ============================================================
        // CPR - STEP 2
        // ============================================================
    
        private void startCprStep2() {
    
            if (!isActionActive ||
                    !currentMode.equals("CPR")) {
    
                return;
            }
    
            cprStep = 2;
    
            imgGuide.setImageResource(
                    R.drawable.guide_cpr_active
            );
    
            if (isThai) {
    
                tvInstruction.setText(
                        "📍 [ขั้นตอนที่ 2/3] กำหนดจุด: วางสันมือตรงกึ่งกลางหน้าอก"
                );
    
                speakTextWithId(
                        "ขั้นตอนที่สอง วางสันมือตรงกึ่งกลางหน้าอก",
                        "CPR_STEP_2"
                );
    
            } else {
    
                tvInstruction.setText(
                        "📍 [Step 2/3] Position: Place the heel of your hand in the center of the chest."
                );
    
                speakTextWithId(
                        "Step two. Place the heel of your hand in the center of the chest.",
                        "CPR_STEP_2"
                );
            }
        }
    
        // ============================================================
        // CPR - STEP 3
        // ============================================================
    
        private void startCprStep3() {
    
            if (!isActionActive ||
                    !currentMode.equals("CPR")) {
    
                return;
            }
    
            cprStep = 3;
    
            if (isThai) {
    
                tvInstruction.setText(
                        "🔊 [ขั้นตอนที่ 3/3] เตรียมตัวกดหน้าอก: 3... 2... 1... เริ่ม!"
                );
    
                /*
                 * สำคัญ:
                 * ห้ามเรียก startMetronome() ตรงนี้
                 *
                 * TTS ต้องพูดจบก่อน
                 */
    
                speakTextWithId(
                        "ขั้นตอนที่สาม เตรียมตัวกดหน้าอก 3... 2... 1... เริ่ม!",
                        "CPR_STEP_3"
                );
    
            } else {
    
                tvInstruction.setText(
                        "🔊 [Step 3/3] Get ready for chest compressions: 3... 2... 1... Begin!"
                );
    
                /*
                 * ภาษาอังกฤษแบบธรรมชาติ
                 */
    
                speakTextWithId(
                        "Step three. Get ready for chest compressions. Three... two... one... Begin!",
                        "CPR_STEP_3"
                );
            }
        }
    
        // ============================================================
        // TTS WITH UTTERANCE ID
        // ============================================================
    
        private void speakTextWithId(
                String text,
                String utteranceId) {
    
            if (tts == null) {
    
                return;
            }
    
            /*
             * QUEUE_FLUSH ใช้ตรงนี้ได้
             * เพราะแต่ละขั้นจะเริ่มหลังจากขั้นก่อนหน้าจบแล้ว
             */
    
            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    utteranceId
            );
        }
    
        // ============================================================
        // NORMAL TTS
        // ============================================================
    
        private void speakText(
                String text) {
    
            if (tts == null) {
    
                return;
            }
    
            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "tts_" +
                            System.currentTimeMillis()
            );
        }
    
        // ============================================================
        // STOP ACTION
        // ============================================================
    
        private void stopAction() {
    
            isActionActive = false;
    
            cprStep = 0;
            guidanceStep = 0;
    
            stopMetronome();
    
            stopOfflineBystanderBeacon();
    
            if (stepHandler != null) {
    
                stepHandler.removeCallbacksAndMessages(
                        null
                );
            }
    
            if (tts != null) {
    
                tts.stop();
            }
    
            updateModeUI();
        }
    
        // ============================================================
        // STOP ACTION + REPORT
        // ============================================================
    
        private void stopActionWithReport() {
    
            long durationSec =
                    (
                            System.currentTimeMillis()
                                    - startTimeMillis
                    ) / 1000;
    
            stopAction();
    
            String durationText =
                    formatTime(
                            durationSec
                    );
    
            String title =
                    isThai
                            ? "📋 รายงานสรุปเหตุการณ์"
                            : "📋 Emergency Summary";
    
            String message;
    
            if (isThai) {
    
                message =
                        "ประเภทเคส: " +
                                getModeName() +
                                "\n\n" +
    
                                "ระยะเวลาช่วยเหลือรวม: " +
                                durationText +
                                "\n\n" +
    
                                "สถานะ: เสร็จสิ้นกระบวนการนำทางฉุกเฉิน\n\n" +
    
                                "พร้อมส่งต่อข้อมูลให้ทีมกู้ชีพ (EMS) เรียบร้อย";
    
            } else {
    
                message =
                        "Emergency type: " +
                                getModeName() +
                                "\n\n" +
    
                                "Total assistance time: " +
                                durationText +
                                "\n\n" +
    
                                "Status: Emergency guidance completed.\n\n" +
    
                                "Information is ready for EMS handoff.";
            }
    
            new AlertDialog.Builder(this)
    
                    .setTitle(title)
    
                    .setMessage(message)
    
                    .setPositiveButton(
                            isThai
                                    ? "ตกลง"
                                    : "OK",
    
                            (dialog, which) ->
                                    dialog.dismiss()
                    )
    
                    .show();
        }
    
        // ============================================================
        // FORMAT TIME
        // ============================================================
    
        private String formatTime(
                long seconds) {
    
            long minutes =
                    seconds / 60;
    
            long remainSeconds =
                    seconds % 60;
    
            return String.format(
                    Locale.getDefault(),
                    "%02d:%02d " +
                            (
                                    isThai
                                            ? "นาที"
                                            : "min"
                            ),
                    minutes,
                    remainSeconds
            );
        }
    
        // ============================================================
        // MODE NAME
        // ============================================================
    
        private String getModeName() {
    
            switch (currentMode) {
    
                case "CPR":
    
                    return isThai
                            ? "CPR (ปั๊มหัวใจ)"
                            : "CPR";
    
                case "CHOKING":
    
                    return isThai
                            ? "สำลักอาหาร"
                            : "Choking";
    
                case "BLEEDING":
    
                    return isThai
                            ? "ห้ามเลือด"
                            : "Severe Bleeding";
    
                case "SEIZURE":
    
                    return isThai
                            ? "อาการชัก"
                            : "Seizure";
    
                default:
    
                    return currentMode;
            }
        }
    
        // ============================================================
        // CPR METRONOME
        // ============================================================
    
        private void startMetronome() {
    
            stopMetronome();
    
            /*
             * ป้องกันกรณีถูกเรียกก่อนเวลา
             */
    
            if (!isActionActive ||
                    !currentMode.equals("CPR") ||
                    cprStep != 3) {
    
                return;
            }
    
            metronomeRunnable =
                    new Runnable() {
    
                        @Override
                        public void run() {
    
                            if (isActionActive &&
                                    currentMode.equals("CPR") &&
                                    cprStep == 3) {
    
                                if (toneGen != null) {
    
                                    toneGen.startTone(
                                            ToneGenerator
                                                    .TONE_PROP_BEEP,
                                            100
                                    );
                                }
    
                                metronomeHandler.postDelayed(
                                        this,
                                        600
                                );
                            }
                        }
                    };
    
            metronomeHandler.post(
                    metronomeRunnable
            );
        }
    
        // ============================================================
        // STOP METRONOME
        // ============================================================
    
        private void stopMetronome() {
    
            if (metronomeHandler != null &&
                    metronomeRunnable != null) {
    
                metronomeHandler.removeCallbacks(
                        metronomeRunnable
                );
            }
        }
    
        // ============================================================
        // ON RESUME
        // ============================================================
    
        @Override
        protected void onResume() {
    
            super.onResume();
            if (sensorManager != null && accelerometer != null && isFallDetectionActive) {
                sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
    
            if (bluetoothAdapter != null) {
    
                try {
    
                    if (bluetoothAdapter.isEnabled()) {
    
                        if (!isScanning) {
    
                            startBystanderScanner();
                        }
    
                    } else {
    
                        updateBleStatus(
                                isThai
                                        ? "⚠️ BLE: Bluetooth ปิดอยู่ — เปิด Bluetooth ได้เลย"
                                        : "⚠️ BLE: Bluetooth is off — please turn it on"
                        );
                    }
    
                } catch (Exception e) {
    
                    updateBleStatus(
                            isThai
                                    ? "❌ BLE: ตรวจสอบ Bluetooth ไม่สำเร็จ"
                                    : "❌ BLE: Failed to check Bluetooth"
                    );
                }
            }
        }
    
        // ============================================================
        // TTS INIT
        // ============================================================
    
        @Override
        public void onInit(
                int status) {
    
            if (status ==
                    TextToSpeech.SUCCESS) {
    
                setTtsLanguage();
            }
        }
    
        // ============================================================
        // SET TTS LANGUAGE
        // ============================================================
    
        private void setTtsLanguage() {
    
            if (tts == null) {
    
                return;
            }
    
            int result;
    
            if (isThai) {
    
                result =
                        tts.setLanguage(
                                new Locale(
                                        "th",
                                        "TH"
                                )
                        );
    
            } else {
    
                /*
                 * ใช้ English (US)
                 */
    
                result =
                        tts.setLanguage(
                                Locale.US
                        );
            }
    
            if (result ==
                    TextToSpeech.LANG_MISSING_DATA ||
                    result ==
                            TextToSpeech.LANG_NOT_SUPPORTED) {
    
                Toast.makeText(
                        this,
                        isThai
                                ? "⚠️ เครื่องไม่มีข้อมูลเสียงภาษาไทย"
                                : "⚠️ English voice data is not available",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    
        // ============================================================
        // ON DESTROY
        // ============================================================
    
        @Override
        protected void onDestroy() {
    
            stopMetronome();
    
            stopOfflineBystanderBeacon();
    
            stopBystanderScanner();
    
            try {
    
                unregisterReceiver(
                        bluetoothStateReceiver
                );
    
            } catch (Exception ignored) {
            }
    
            if (stepHandler != null) {
    
                stepHandler.removeCallbacksAndMessages(
                        null
                );
            }
    
            if (tts != null) {
    
                tts.stop();
    
                tts.shutdown();
            }
    
            if (toneGen != null) {
    
                toneGen.release();
            }
            if (sensorManager != null && sensorEventListener != null) {
                sensorManager.unregisterListener(sensorEventListener);
            }
            cancelFallCountdown();
    
            super.onDestroy();
        }
    
        // ============================================================
        // AUTO FALL / IMPACT DETECTION METHODS
        // ============================================================
        private void initFallDetection() {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
    
            if (accelerometer != null) {
                sensorEventListener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        if (!isFallDetectionActive || isActionActive) {
                            return;
                        }
    
                        float x = event.values[0];
                        float y = event.values[1];
                        float z = event.values[2];
    
                        double gForce = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
    
                        if (gForce > 2.7) {
                            triggerFallCountdown();
                        }
                    }
    
                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                };
            }
        }
    
        private void triggerFallCountdown() {
            if (fallCountdownDialog != null && fallCountdownDialog.isShowing()) {
                return;
            }
    
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(1000);
                }
            }
    
            remainingSeconds = 10;
    
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(isThai ? "⚠️ ตรวจพบการกระแทก/อุบัติเหตุ!" : "⚠️ Impact Detected!")
                    .setCancelable(false);
    
            final TextView tvCountdownMessage = new TextView(this);
            tvCountdownMessage.setPadding(50, 40, 50, 20);
            tvCountdownMessage.setTextSize(16);
            tvCountdownMessage.setGravity(Gravity.CENTER);
            builder.setView(tvCountdownMessage);
    
            builder.setPositiveButton(isThai ? "ฉันปลอดภัยดี (ยกเลิก)" : "I'm OK (Cancel)", (dialog, which) -> {
                cancelFallCountdown();
                Toast.makeText(this, isThai ? "✅ ยกเลิกการส่งสัญญาณฉุกเฉินแล้ว" : "✅ Emergency alert cancelled", Toast.LENGTH_SHORT).show();
            });
    
            fallCountdownDialog = builder.create();
            fallCountdownDialog.show();
    
            fallCountdownRunnable = new Runnable() {
                @Override
                public void   run() {
                    if (remainingSeconds > 0) {
                        tvCountdownMessage.setText(
                                (isThai ? "ระบบตรวจพบแรงกระแทกผิดปกติ!\nกำลังส่งสัญญาณ BLE และพิกัดฉุกเฉินใน...\n\n⏰ " : "Unusual impact detected!\nBroadcasting BLE and GPS in...\n\n⏰ ")
                                        + remainingSeconds + (isThai ? " วินาที" : " seconds")
                        );
                        remainingSeconds--;
                        fallHandler.postDelayed(this, 1000);
                    } else {
                        if (fallCountdownDialog != null && fallCountdownDialog.isShowing()) {
                            fallCountdownDialog.dismiss();
                        }
                        executeAutomaticEmergencyBroadcast();
                    }
                }
            };
            fallHandler.post(fallCountdownRunnable);
        }
    
        private void cancelFallCountdown() {
            if (fallCountdownRunnable != null) {
                fallHandler.removeCallbacks(fallCountdownRunnable);
            }
            if (fallCountdownDialog != null && fallCountdownDialog.isShowing()) {
                fallCountdownDialog.dismiss();
            }
        }
    
        private void executeAutomaticEmergencyBroadcast() {
            currentMode = "CPR";
            updateModeUI();
    
            // 1. ส่งสัญญาณ BLE ออกรอบตัว
            startOfflineBystanderBeacon();
    
            // 2. แสดงหน้าต่างแจ้งเตือน พร้อมปุ่มกดหยุดและรีเซ็ตกลับเป็นปกติ
            new AlertDialog.Builder(this)
                    .setTitle(isThai ? "🚨 กำลังส่งสัญญาณฉุกเฉิน (สด ๆ)" : "🚨 Broadcasting Emergency!")
                    .setMessage(
                            isThai
                                    ? "ระบบกำลังกระจายสัญญาณ BLE Beacon รอบตัวอยู่!\n\n" +
                                      "📡 นำมือถืออีกเครื่องมาสแกนดูจะเจอสัญญาณจริง\n" +
                                      "📍 พิกัด GPS: (จำลอง 13.7563° N, 100.5018° E)\n\n" +
                                      "-------------------------------------\n" +
                                      "💡 เมื่อโชว์กรรมการเสร็จแล้ว ให้กดปุ่มด้านล่างเพื่อหยุดการส่งและรีเซ็ตแอปกลับสู่หน้าปกติครับ"
                                    : "Broadcasting real BLE beacon now!\n\n" +
                                      "📡 Another device can detect this signal.\n" +
                                      "📍 GPS: (Simulated)\n\n" +
                                      "-------------------------------------\n" +
                                      "💡 Click below to stop and reset."
                    )
                    .setCancelable(false)
                    // 🛑 เพิ่มปุ่มนี้เข้าไปเพื่อให้กดหยุดและรีเซ็ตได้ทันที
                    .setPositiveButton(isThai ? "⏹️ หยุดส่งสัญญาณและรีเซ็ต" : "⏹️ Stop & Reset", (dialog, which) -> {
    
                        // สั่งตัดสัญญาณ BLE ทันที
                        stopOfflineBystanderBeacon();
    
                        // รีเซ็ตสถานะแอปกลับเป็นหน้าจอปกติ
                        stopAction();
    
                        Toast.makeText(this, isThai ? "✅ รีเซ็ตระบบกลับสู่หน้าปกติแล้ว" : "✅ System reset to normal", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .show();
        }
    }
