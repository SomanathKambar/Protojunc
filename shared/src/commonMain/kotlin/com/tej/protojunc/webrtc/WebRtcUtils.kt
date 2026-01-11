package com.tej.protojunc.webrtc

import com.shepeliev.webrtckmp.IceCandidate
import com.tej.protojunc.models.IceCandidateModel

fun createIceCandidate(model: IceCandidateModel): IceCandidate = IceCandidate(
    sdpMid = model.sdpMid ?: "",
    sdpMLineIndex = model.sdpMLineIndex,
    candidate = model.sdp
)

fun IceCandidate.toModel(): IceCandidateModel = IceCandidateModel(
    sdp = this.candidate,
    sdpMid = this.sdpMid,
    sdpMLineIndex = this.sdpMLineIndex
)
