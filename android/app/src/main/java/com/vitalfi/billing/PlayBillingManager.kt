package com.vitalfi.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayProductUi(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val rawProductDetails: ProductDetails,
)

class PlayBillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onPurchaseUnlocked: (productId: String, orderId: String) -> Unit,
) : PurchasesUpdatedListener {

    private val _products = MutableStateFlow<List<PlayProductUi>>(emptyList())
    val products: StateFlow<List<PlayProductUi>> = _products.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun start() {
        if (billingClient.isReady) return

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isReady.value = true
                    queryProducts()
                    restorePurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _isReady.value = false
            }
        })
    }

    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PRO_SUPPORTER)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_INSTITUTIONAL)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_DONATION_TIP)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val uiList = productDetailsList.map { details ->
                    val offer = details.oneTimePurchaseOfferDetails
                    PlayProductUi(
                        productId = details.productId,
                        title = details.name,
                        description = details.description,
                        formattedPrice = offer?.formattedPrice ?: "—",
                        rawProductDetails = details,
                    )
                }
                _products.value = uiList
            }
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val targetProduct = _products.value.firstOrNull { it.productId == productId }
        if (targetProduct == null) {
            Log.w(TAG, "Product not found: $productId")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(targetProduct.rawProductDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgeParams) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        for (prodId in purchase.products) {
                            onPurchaseUnlocked(prodId, purchase.orderId ?: "PLAY_${System.currentTimeMillis()}")
                        }
                    }
                }
            } else {
                for (prodId in purchase.products) {
                    onPurchaseUnlocked(prodId, purchase.orderId ?: "PLAY_${System.currentTimeMillis()}")
                }
            }
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
    }

    fun destroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    companion object {
        private const val TAG = "PlayBillingManager"
        const val PRODUCT_PRO_SUPPORTER = "vitalfi_pro_supporter"
        const val PRODUCT_INSTITUTIONAL = "vitalfi_institutional"
        const val PRODUCT_DONATION_TIP = "vitalfi_donation_tip"
    }
}
