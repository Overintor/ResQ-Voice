package com.example.resq_voice;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
public class HomeActivity extends AppCompatActivity {

    private MaterialCardView cardSosBanner, cardCpr, cardChoking, cardBleeding, cardSeizure;
    private Button btnMedicalId, btnLanguage, btnCall1669, btnGpsShare, btnFlashlight;
    private TextView tvHomeTitle, tvSosTitle, tvSosDesc, tvGuideTitle;
    private TextView tvCprTitle, tvCprDesc, tvChokingTitle, tvChokingDesc;
    private TextView tvBleedingTitle, tvBleedingDesc, tvSeizureTitle, tvSeizureDesc;

    private boolean isThai = true;
    private static final String PREFS_NAME = "ResQPrefs";
    private static final String KEY_IS_THAI="***";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // โหลดค่าภาษาที่เคยบันทึกไว้ถาวร
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isThai = prefs.getBoolean("***", true);

        // เชื่อม UI ทั้งหมด
        tvHomeTitle = findViewById(R.id.tvHomeTitle);
        btnLanguage = findViewById(R.id.btnLanguage);
        btnMedicalId = findViewById(R.id.btnMedicalId);

        tvSosTitle = findViewById(R.id.tvSosTitle);
        tvSosDesc = findViewById(R.id.tvSosDesc);
        tvSosDesc = findViewById(R.id.tvSosDesc);
        tvGuideTitle = findViewById(R.id.tvGuideTitle);

        tvCprTitle = findViewById(R.id.tvCprTitle);
        tvCprDesc = findViewById(R.id.tvCprDesc);
        tvChokingTitle = findViewById(R.id.tvChokingTitle);
        tvChokingDesc = findViewById(R.id.tvChokingDesc);
        tvBleedingTitle = findViewById(R.id.tvBleedingTitle);
        tvBleedingDesc = findViewById(R.id.tvBleedingDesc);
        tvSeizureTitle = findViewById(R.id.tvSeizureTitle);
        tvSeizureDesc = findViewById(R.id.tvSeizureDesc);

        cardSosBanner = findViewById(R.id.cardSosBanner);
        cardCpr = findViewById(R.id.cardCpr);
        cardChoking = findViewById(R.id.cardChoking);
        cardBleeding = findViewById(R.id.cardBleeding);
        cardSeizure = findViewById(R.id.cardSeizure);

        btnCall1669 = findViewById(R.id.btnCall1669);
        btnGpsShare = findViewById(R.id.btnGpsShare);
        btnFlashlight = findViewById(R.id.btnFlashlight);

        // อัปเดตภาษาเริ่มต้นบนหน้า Home
        updateHomeLanguageUI();

        // ปุ่มเปลี่ยนภาษา
        btnLanguage.setOnClickListener(v -> {

            isThai = !isThai;

            // บันทึกภาษาล่าสุดไว้ถาวร
            prefs.edit()
                    .putBoolean("***", isThai)
                    .apply();

            updateHomeLanguageUI();

            Toast.makeText(
                    this,
                    isThai
                            ? "🇹🇭 เปลี่ยนเป็นภาษาไทยแล้ว"
                            : "🇬🇧 Changed to English",
                    Toast.LENGTH_SHORT
            ).show();
        });

        // กดเลือกโหมดต่างๆ ส่งค่าภาษาและโหมดไป MainActivity
        cardCpr.setOnClickListener(v -> openMainActivity("CPR"));
        cardChoking.setOnClickListener(v -> openMainActivity("CHOKING"));
        cardBleeding.setOnClickListener(v -> openMainActivity("BLEEDING"));
        cardSeizure.setOnClickListener(v -> openMainActivity("SEIZURE"));
        cardSosBanner.setOnClickListener(v -> openMainActivity("CPR"));

        btnMedicalId.setOnClickListener(v -> showMedicalIdDialog());
        btnCall1669.setOnClickListener(v -> showSimulationDialog(
                isThai ? "📞 จำลองการโทร 1669" : "📞 Simulated call to 1669",
                isThai ? "กำลังเชื่อมต่อไปยังศูนย์การแพทย์ฉุกเฉิน 1669...\n\n[สถานะจำลอง]: เชื่อมต่อสำเร็จ" : "Connecting to emergency medical services at 1669...\n\n[Simulation]: Connection successful."
        ));
        btnGpsShare.setOnClickListener(v -> showSimulationDialog(
                isThai ? "📍 จำลองการแชร์พิกัด GPS" : "📍 Simulated GPS location sharing",
                isThai ? "กำลังดึงพิกัดผ่านดาวเทียม...\n\n[สถานะจำลอง]: ส่งพิกัดสำเร็จ\nLatitude: 13.7563° N\nLongitude: 100.5018° E" : "Retrieving your GPS location...\n\n[Simulation]: Location shared successfully.\nLatitude: 13.7563° N\nLongitude: 100.5018° E"
        ));
        btnFlashlight.setOnClickListener(v -> Toast.makeText(
                this, isThai ? "🔦 จำลองเปิด/ปิด ไฟฉายฉุกเฉิน" : "🔦 Emergency flashlight simulated", Toast.LENGTH_SHORT
        ).show());
    }

    // อัปเดตภาษา Home
    private void updateHomeLanguageUI() {
        if (btnLanguage != null) {
            btnLanguage.setText(isThai ? "🌐 ไทย" : "🌐 English");
        }
        if (tvHomeTitle != null) {
            tvHomeTitle.setText(isThai ? "เลือกสถานการณ์ฉุกเฉิน" : "Select Emergency");
        }
        if (tvSosTitle != null) {
            tvSosTitle.setText(isThai ? "SOS ฉุกเฉินด่วน" : "Emergency SOS");
        }
        if (tvSosDesc != null) {
            tvSosDesc.setText(isThai ? "แตะเพื่อเริ่มระบบเสียงนำทางและส่งสัญญาณออฟไลน์" : "Tap to start voice guidance & offline signal");
        }
        if (tvGuideTitle != null) {
            tvGuideTitle.setText(isThai ? "คู่มือการช่วยเหลือ (เลือกสถานการณ์)" : "Emergency Guide (Select Scenario)");
        }

        // การ์ด CPR
        if (tvCprTitle != null) tvCprTitle.setText(isThai ? "CPR (ปั๊มหัวใจ)" : "CPR");
        if (tvCprDesc != null) tvCprDesc.setText(isThai ? "หัวใจหยุดเต้น" : "Cardiac Arrest");

        // การ์ด สำลัก
        if (tvChokingTitle != null) tvChokingTitle.setText(isThai ? "สำลักอาหาร" : "Choking");
        if (tvChokingDesc != null) tvChokingDesc.setText(isThai ? "อาหารติดคอ" : "Blocked Airway");

        // การ์ด ห้ามเลือด
        if (tvBleedingTitle != null) tvBleedingTitle.setText(isThai ? "ห้ามเลือด" : "Severe Bleeding");
        if (tvBleedingDesc != null) tvBleedingDesc.setText(isThai ? "แผลฉกรรจ์" : "Heavy Blood Loss");

        // การ์ด อาการชัก
        if (tvSeizureTitle != null) tvSeizureTitle.setText(isThai ? "อาการชัก" : "Seizure");
        if (tvSeizureDesc != null) tvSeizureDesc.setText(isThai ? "ชักเกร็ง/หมดสติ" : "Convulsion / Fainting");

        // ปุ่มด่วน
        if (btnFlashlight != null) {
            btnFlashlight.setText(isThai ? "🔦 ไฟฉาย" : "🔦 Flashlight");
        }
    }

    private void openMainActivity(String mode) {
        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        intent.putExtra("SELECTED_MODE", mode);
        intent.putExtra("IS_THAI", isThai); // ส่งค่าภาษาติดตัวไปหน้าหลักด้วย
        startActivity(intent);
    }

    private void showMedicalIdDialog() {
        new AlertDialog.Builder(this)
                .setTitle(isThai ? "🩺 บัตรประจำตัวผู้ป่วย (Medical ID)" : "🩺 Medical ID")
                .setMessage(isThai ? "ชื่อ-นามสกุล: คุณออม (Aom)\nอายุ: 18 ปี | กรุ๊ปเลือด: O+\n\n⚠️ โรคประจำตัว / ภูมิแพ้:\n- ไม่มี\n\n📞 ผู้ติดต่อฉุกเฉิน:\n- มารดา : 091-XXX-XXXX" : "Name: Aom\nAge: 18 | Blood type: O+\n\n⚠️ Medical conditions / Allergies:\n- None\n\n📞 Emergency contact:\n- Mother : 091-XXX-XXXX")
                .setPositiveButton(isThai ? "ปิด" : "Close", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showSimulationDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(isThai ? "ตกลง" : "OK", (dialog, which) -> dialog.dismiss())
                .show();
    }
}