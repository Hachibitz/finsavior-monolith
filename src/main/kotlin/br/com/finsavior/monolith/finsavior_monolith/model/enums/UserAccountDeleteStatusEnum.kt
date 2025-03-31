package br.com.finsavior.monolith.finsavior_monolith.model.enums

enum class UserAccountDeleteStatusEnum(val id: Int, val description: String) {
    IN_PROCESS(1, "in_process"),
    FAILED(2, "failed"),
    FINISHED(3, "finished");

    companion object {
        fun fromId(id: Int?): UserAccountDeleteStatusEnum {
            for (status in UserAccountDeleteStatusEnum.entries) {
                if (status.id == id) {
                    return status
                }
            }
            throw IllegalArgumentException("Invalid UserAccountDeleteStatus id: $id")
        }
    }
}