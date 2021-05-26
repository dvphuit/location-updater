package cuongdev.app.smartview.model

data class BaseResponse(val result: Int, val message: String)

fun BaseResponse.isSuccess() = result == 1