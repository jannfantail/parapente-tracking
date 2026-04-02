<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0a0f1e"
    android:fillViewport="true">

<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center_horizontal"
    android:padding="24dp"
    android:paddingTop="48dp">

    <!-- Header -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="🪂 PARAPENTE TRACKER"
        android:textColor="#7dd3fc"
        android:textSize="20sp"
        android:textStyle="bold"
        android:letterSpacing="0.1"
        android:layout_marginBottom="4dp"/>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="v1.0.003 — GPS + Accéléromètre"
        android:textColor="#475569"
        android:textSize="11sp"
        android:layout_marginBottom="32dp"/>

    <!-- Champ nom pilote -->
    <EditText
        android:id="@+id/etPilotName"
        android:layout_width="match_parent"
        android:layout_height="52dp"
        android:hint="Nom du pilote / ID élève"
        android:textColor="#e2e8f0"
        android:textColorHint="#475569"
        android:background="@drawable/input_bg"
        android:padding="14dp"
        android:textSize="16sp"
        android:inputType="text"
        android:layout_marginBottom="12dp"/>

    <!-- Statut -->
    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="○ Inactif"
        android:textColor="#ef4444"
        android:textSize="14sp"
        android:layout_marginBottom="20dp"/>

    <!-- Stats live : grille 2x2 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginBottom="8dp">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical"
            android:background="#0f172a"
            android:padding="14dp"
            android:layout_marginEnd="6dp">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="ALTITUDE" android:textColor="#475569" android:textSize="9sp" android:letterSpacing="0.1"/>
            <TextView android:id="@+id/tvAlt" android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="— m" android:textColor="#0ea5e9" android:textSize="22sp" android:textStyle="bold"/>
        </LinearLayout>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical"
            android:background="#0f172a"
            android:padding="14dp"
            android:layout_marginStart="6dp">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="VITESSE" android:textColor="#475569" android:textSize="9sp" android:letterSpacing="0.1"/>
            <TextView android:id="@+id/tvSpd" android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="— km/h" android:textColor="#22c55e" android:textSize="22sp" android:textStyle="bold"/>
        </LinearLayout>
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginBottom="20dp">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical"
            android:background="#0f172a"
            android:padding="14dp"
            android:layout_marginEnd="6dp">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="ACCÉLÉRATION" android:textColor="#475569" android:textSize="9sp" android:letterSpacing="0.1"/>
            <TextView android:id="@+id/tvG" android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="— G" android:textColor="#22c55e" android:textSize="22sp" android:textStyle="bold"/>
        </LinearLayout>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical"
            android:background="#0f172a"
            android:padding="14dp"
            android:layout_marginStart="6dp">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="ENVOIS" android:textColor="#475569" android:textSize="9sp" android:letterSpacing="0.1"/>
            <TextView android:id="@+id/tvCount" android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="0 envois" android:textColor="#94a3b8" android:textSize="22sp" android:textStyle="bold"/>
        </LinearLayout>
    </LinearLayout>

    <!-- Boutons -->
    <Button
        android:id="@+id/btnStart"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="▶ DÉMARRER LE TRACKING"
        android:textColor="#0a0f1e"
        android:backgroundTint="#0ea5e9"
        android:textStyle="bold"
        android:letterSpacing="0.05"
        android:layout_marginBottom="12dp"/>

    <Button
        android:id="@+id/btnStop"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="⬛ ARRÊTER"
        android:textColor="#ef4444"
        android:backgroundTint="#1e293b"
        android:enabled="false"/>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="GPS actif en arrière-plan • Accéléromètre 50Hz\nFacteur de charge : 1G = vol normal"
        android:textColor="#1e3a5f"
        android:textSize="11sp"
        android:gravity="center"
        android:layout_marginTop="24dp"/>

</LinearLayout>
</ScrollView>
