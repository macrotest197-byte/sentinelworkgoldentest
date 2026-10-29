package com.sentinel.recaudacion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sentinel.recaudacion.data.settings.ConsentStore
import com.sentinel.recaudacion.data.settings.DeviceIdentityStore
import com.sentinel.recaudacion.data.settings.DeviceRegistrationStatus
import com.sentinel.recaudacion.ui.consent.MonitoringPolicyScreen
import com.sentinel.recaudacion.ui.navigation.SentinelNavHost
import com.sentinel.recaudacion.ui.setup.DeviceNameScreen
import com.sentinel.recaudacion.ui.theme.SentinelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Dos portones antes de la navegación, en este orden:
        //  1. nombre del celular: sin nombre el equipo no se identifica en las alertas;
        //  2. consentimiento: sin aceptar no hay pantalla de inicio ni accesos a secciones.
        val consent = ConsentStore(this)
        val identity = DeviceIdentityStore(this)

        setContent {
            SentinelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var named by remember {
                        mutableStateOf(identity.status == DeviceRegistrationStatus.REGISTRADO)
                    }
                    var accepted by remember { mutableStateOf(consent.isAccepted()) }

                    when {
                        !named -> DeviceNameScreen(
                            onSave = { name ->
                                identity.setAdministratorName(name)
                                named = true
                            },
                        )
                        !accepted -> MonitoringPolicyScreen(
                            onboarding = true,
                            onAccept = {
                                consent.accept(identity.id)
                                accepted = true
                            },
                            onBack = {},
                        )
                        else -> SentinelNavHost()
                    }
                }
            }
        }
    }
}