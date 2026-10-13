package com.safakstream.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class NewTvOtpResponse(
    @JsonProperty("otp") val otp: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("usertoken") val usertoken: String? = null,
    @JsonProperty("pub_msg") val pub_msg: String? = null,
    @JsonProperty("pub_msg_f_size") val pub_msg_f_size: Int? = null,
    @JsonProperty("pub_msg_color") val pub_msg_color: String? = null,
    @JsonProperty("error_msg") val error_msg: String? = null
)
