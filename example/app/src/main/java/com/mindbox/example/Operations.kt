package com.mindbox.example

import android.app.AlertDialog
import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.models.MindboxError
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequest
import cloud.mindbox.mobile_sdk.models.operation.request.ProductRequest
import cloud.mindbox.mobile_sdk.models.operation.request.RecommendationRequest
import cloud.mindbox.mobile_sdk.models.operation.request.ViewProductRequest
import cloud.mindbox.mobile_sdk.models.operation.response.OperationResponse

fun sendAsync(type: AsyncOperationType, context: Context) {
    when (type) {
        AsyncOperationType.OPERATION_BODY -> viewProductAsync(context)
        AsyncOperationType.OPERATION_BODY_JSON -> viewProductAsyncJson(context)
    }
}

fun sendSync(type: SyncOperationType, context: Context) {
    when (type) {
        SyncOperationType.OPERATION_BODY -> getProductRecoSync(context)
        SyncOperationType.OPERATION_BODY_JSON -> getProductRecoSyncJsonBody(context)
        SyncOperationType.OPERATION_BODY_WITH_CUSTOM_RESPONSE -> getProductRecoSyncWithCustomClassOnResponse(
            context
        )
    }
}

private fun viewProductAsync(context: Context) {
    Mindbox.executeAsyncOperation(
        context = context,
        operationSystemName = "viewProduct",
        operationBody = OperationBodyRequest(
            viewProductRequest = ViewProductRequest(
                product = ProductRequest(
                    ids = Ids("website" to "12345")
                )
            )
        )
    )
}

private fun viewProductAsyncJson(context: Context) {
    Mindbox.executeAsyncOperation(
        context = context,
        operationSystemName = "viewProduct",
        operationBodyJson = """{
                    "viewProduct": {
                       "product": {
                          "ids": {
                            "website": "123"
                            }
                        }
                    }
            }"""
    )
}

private fun getProductRecoSync(context: Context) {
    Mindbox.executeSyncOperation(
        context = context,
        operationSystemName = "productReco.sync",
        operationBody = OperationBodyRequest(
            recommendation = RecommendationRequest(
                limit = 3,
                product = ProductRequest(
                    ids = Ids("website" to "12345")
                )
            )
        ),
        onSuccess = { response -> handleSuccess(context, response, null) },
        onError = { error -> handleError(context, error) }
    )
}

private fun getProductRecoSyncJsonBody(context: Context) {
    Mindbox.executeSyncOperation(
        context = context,
        operationSystemName = "productReco.sync",
        operationBodyJson = """{
	"recommendation": {
		"limit": 3,
		"product": {
			"ids": {
				"website": "12345"
			}
		}
	}
}""",
        onSuccess = { response -> handleSuccess(context, null, response) },
        onError = { error -> handleError(context, error) }
    )
}

private fun getProductRecoSyncWithCustomClassOnResponse(context: Context) {
    Mindbox.executeSyncOperation(
        context = context,
        operationSystemName = "productReco.sync",
        operationBody = OperationBodyRequest(
            recommendation = RecommendationRequest(
                limit = 3,
                product = ProductRequest(
                    ids = Ids("website" to "12345")
                )
            )
        ),
        classOfV = OperationResponse::class.java,
        onSuccess = { },
        onError = { }
    )
}

fun handleSuccess(
    context: Context,
    response: OperationResponse? = null,
    responseString: String? = null
) {
    val result = response ?: responseString
    AlertDialog.Builder(context)
        .setTitle("Success")
        .setMessage("Operation completed successfully: ${result.toString()}")
        .setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}

fun handleError(context: Context, error: MindboxError) {
    AlertDialog.Builder(context)
        .setTitle("Error")
        .setMessage("Operation failed: $error")
        .setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}

