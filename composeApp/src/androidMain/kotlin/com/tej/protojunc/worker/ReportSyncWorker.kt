package com.tej.protojunc.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tej.protojunc.p2p.data.db.SurgicalReportDao
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReportSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val reportDao: SurgicalReportDao by inject()

    override suspend fun doWork(): Result {
        val unsyncedReports = reportDao.getUnsyncedReports()
        if (unsyncedReports.isEmpty()) return Result.success()

        val client = HttpClient()
        var success = true

        val host = com.tej.protojunc.signalingServerHost
        val port = com.tej.protojunc.signalingServerPort

        for (report in unsyncedReports) {
            try {
                val response = client.post("http://$host:$port/api/reports") {
                    setBody("Patient: ${report.patientId}\nContent: ${report.reportContent}")
                }
                if (response.status == HttpStatusCode.Created) {
                    reportDao.markAsSynced(report.id)
                } else {
                    success = false
                }
            } catch (e: Exception) {
                success = false
            }
        }
        client.close()

        return if (success) Result.success() else Result.retry()
    }
}
