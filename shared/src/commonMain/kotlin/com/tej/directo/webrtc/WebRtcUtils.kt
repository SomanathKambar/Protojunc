package com.tej.directo.webrtc

import com.shepeliev.webrtckmp.IceCandidate
import com.tej.directo.models.IceCandidateModel

expect fun createIceCandidate(model: IceCandidateModel): IceCandidate
expect fun IceCandidate.toModel(): IceCandidateModel
