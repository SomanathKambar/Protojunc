package com.tej.protojunc.core.signaling.di

import com.tej.protojunc.core.signaling.CommunicationEngine
import com.tej.protojunc.core.signaling.xmpp.XmppCommunicationEngine
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val androidSignalingModule = module {
    factory<CommunicationEngine>(named("XMPP")) {
        XmppCommunicationEngine()
    }
}
