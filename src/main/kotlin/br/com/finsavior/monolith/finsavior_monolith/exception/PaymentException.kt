package br.com.finsavior.monolith.finsavior_monolith.exception

/**
 * Machine-readable payment error codes. The frontend translates these to the
 * user's locale; [PaymentException.message] is only a Portuguese fallback for
 * clients that don't know the code yet.
 */
enum class PaymentErrorCode {
    PLAY_PRODUCT_UNKNOWN,
    PLAY_SUBSCRIPTION_INACTIVE,
    PLAY_PURCHASE_LINKED_TO_OTHER_ACCOUNT,
    PLAY_BILLING_NOT_CONFIGURED,
    PLAY_BILLING_PERMISSION_DENIED,
    PLAY_PURCHASE_NOT_FOUND,
    PLAY_VERIFY_FAILED,
    PLAY_MANAGED_IN_STORE,
    SUBSCRIPTION_OTHER_PROVIDER,
    GENERIC,
}

class PaymentException : RuntimeException {
    val errorCode: PaymentErrorCode

    constructor(message: String, errorCode: PaymentErrorCode = PaymentErrorCode.GENERIC) : super(message) {
        this.errorCode = errorCode
    }

    constructor(message: String, cause: Throwable, errorCode: PaymentErrorCode = PaymentErrorCode.GENERIC) : super(message, cause) {
        this.errorCode = errorCode
    }

    constructor(cause: Throwable) : super(cause) {
        this.errorCode = PaymentErrorCode.GENERIC
    }
}
