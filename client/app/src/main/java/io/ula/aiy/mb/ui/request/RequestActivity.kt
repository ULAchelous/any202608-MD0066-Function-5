package io.ula.aiy.mb.ui.request

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.ula.aiy.mb.R
import io.ula.aiy.mb.ui.request.card.CardScreen
import io.ula.aiy.mb.ui.request.communicate.CommunicateScreen
import io.ula.aiy.mb.ui.request.verify.ClaimAnalysisData
import com.google.gson.JsonObject
import io.ula.aiy.mb.ui.request.extract.ExtractScreen
import io.ula.aiy.mb.ui.request.verify.VerifyScreen
import io.ula.aiy.mb.ui.theme.MythBustersTheme
import io.ula.aiy.mb.utils.SharedDataHolder

enum class SRC_TYPE{
    WX_ARTICLE,
    TEXT,
    IMAGE
}

sealed class RequestScreen(val route: String) {
    object Extract : RequestScreen("extract")
    object Verify : RequestScreen("verify")
    object Communicate : RequestScreen("communicate")
    object Card : RequestScreen("card")
}

class RequestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val receivedLink = intent.getStringExtra("link_data")  ?: ""
        // Retrieve large image data from the in-memory holder instead of
        // the Intent extra to avoid Binder TransactionTooLargeException.
        val imageBase64 = SharedDataHolder.takeImageBase64() ?: ""
        val typeName = intent.getStringExtra("type") ?: SRC_TYPE.TEXT.name
        val srcType = try { SRC_TYPE.valueOf(typeName) } catch (e: Exception) { SRC_TYPE.TEXT }

        val finalContent = if (srcType == SRC_TYPE.IMAGE) imageBase64 else receivedLink

        enableEdgeToEdge()
        setContent {
            MythBustersTheme {
                val navController = rememberNavController()
                
                // Shared States
                var extractResult by remember { mutableStateOf(JsonObject()) }
                var verifyResult by remember { mutableStateOf(JsonObject()) }
                var selectedTarget by remember { mutableStateOf("elder") }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = RequestScreen.Extract.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(RequestScreen.Extract.route) {
                            ExtractScreen(
                                content = finalContent,
                                type = srcType,
                                onNavigateToVerify = { selectedClaim, postData ->
                                    extractResult = postData
                                    navController.navigate("${RequestScreen.Verify.route}/$selectedClaim")
                                }
                            )
                        }
                        composable(
                            route = "${RequestScreen.Verify.route}/{claim}"
                        ) { backStackEntry ->
                            val claim = backStackEntry.arguments?.getString("claim") ?: ""
                            VerifyScreen(
                                claim = claim,
                                onNavigateToCommunicate = { claimText, verifyJsonObject ->
                                    verifyResult = verifyJsonObject
                                    navController.navigate("${RequestScreen.Communicate.route}/$claimText")
                                }
                            )
                        }
                        composable(
                            route = "${RequestScreen.Communicate.route}/{claim}"
                        ) { backStackEntry ->
                            val claim = backStackEntry.arguments?.getString("claim") ?: ""
                            CommunicateScreen(
                                claim = claim,
                                onBackToMain = {
                                    finish()
                                },
                                onNavigateToCard = { target ->
                                    selectedTarget = target
                                    navController.navigate(RequestScreen.Card.route)
                                }
                            )
                        }
                        composable(RequestScreen.Card.route) {
                            CardScreen(
                                extractData = extractResult,
                                verifyData = verifyResult,
                                target = selectedTarget,
                                onBackToMain = {
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}




