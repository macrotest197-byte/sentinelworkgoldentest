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
import com.sentinel.recaudacion.ui.consent.MonitoringPolicyScreen
import com.sentinel.recaudacion.ui.navigation.SentinelNavHost
import com.sentinel.recaudacion.ui.theme.SentinelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // El consentimiento se resuelve antes de montar la navegación: sin aceptar no hay
        // pantalla de inicio, ni accesos directos a secciones.
        val consent = ConsentStore(this)
        val identity = DeviceIdentityStore(this)

        setContent {
            SentinelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var accepted by remember { mutableStateOf(consent.isAccepted()) }

                    if (accepted) {
                        SentinelNavHost()
                    } else {
                        MonitoringPolicyScreen(
                            onboarding = true,
                            onAccept = {
                                consent.accept(identity.id)
                                accepted = true
                            },
                            onBack = {},
                        )
                    }
                }
            }
        }
    }
}