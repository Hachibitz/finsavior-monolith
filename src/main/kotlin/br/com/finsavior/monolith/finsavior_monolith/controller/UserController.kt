package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.ChangePasswordRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.DeleteAccountRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpdateProfileRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("user")
class UserController(
    private val userService: UserService
) {

    @PostMapping("/delete-account")
    @ResponseStatus(HttpStatus.OK)
    fun deleteAccountAndData(@RequestBody deleteAccountRequest: DeleteAccountRequestDTO) =
        userService.deleteAccountProcessStart(deleteAccountRequest)

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.OK)
    fun changeAccountPassword(@RequestBody changePasswordRequestDTO: ChangePasswordRequestDTO) =
        userService.changeAccountPassword(changePasswordRequestDTO)

    @PostMapping("/profile-data/upload-picture")
    @ResponseStatus(HttpStatus.OK)
    fun uploadProfilePicture(@RequestParam profilePicture: MultipartFile) =
        userService.uploadProfilePicture(profilePicture)

    @GetMapping("/get-profile-data")
    @ResponseStatus(HttpStatus.OK)
    fun getProfileData() =
        userService.getProfileData()

    @PatchMapping("/profile-data/update-profile")
    @ResponseStatus(HttpStatus.OK)
    fun updateProfile(
        @RequestParam(value = "profilePicture", required = false) profilePicture: MultipartFile?,
        @RequestParam(value = "firstName", required = false) firstName: String?,
        @RequestParam(value = "lastName", required = false) lastName: String?
    ) {
        val updateProfileRequest: UpdateProfileRequestDTO = UpdateProfileRequestDTO(
            firstName,
            lastName
        )

        userService.updateProfile(profilePicture, updateProfileRequest)
    }
}