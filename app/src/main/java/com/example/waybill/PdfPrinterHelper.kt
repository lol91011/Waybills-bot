package com.example.waybill

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

object PdfPrinterHelper {

    fun printWaybill(context: Context, state: WaybillUiState) {
        val rawTemplate = try {
            context.assets.open("form3_template.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            "<html><body><h3>Ошибка чтения шаблона form3_template.html из assets</h3></body></html>"
        }

        val routeRows = if (state.routePoints.size > 1) {
            val distPerSegment = state.calculatedDistance / (state.routePoints.size - 1)
            state.routePoints.zipWithNext().mapIndexed { i, (from, to) ->
                """
                <tr>
                    <td>${i + 1}</td>
                    <td>—</td>
                    <td style="text-align:left;">${from.address}</td>
                    <td style="text-align:left;">${to.address}</td>
                    <td>08:00</td>
                    <td>17:00</td>
                    <td>${"%.1f".format(distPerSegment)}</td>
                    <td></td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        } else {
            state.routePoints.mapIndexed { i, p ->
                """
                <tr>
                    <td>${i + 1}</td>
                    <td>—</td>
                    <td style="text-align:left;">${p.address}</td>
                    <td style="text-align:left;">г. Волгоград</td>
                    <td>08:00</td>
                    <td>17:00</td>
                    <td>${"%.1f".format(state.calculatedDistance)}</td>
                    <td></td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        }

        val filledHtml = rawTemplate
            .replace("{{DATE}}", state.dateString)
            .replace("{{MODEL}}", state.vehicleModel)
            .replace("{{PLATE}}", state.vehiclePlate)
            .replace("{{DRIVER}}", state.driverName)
            .replace("{{START_ODO}}", state.startOdometer)
            .replace("{{END_ODO}}", state.endOdometer)
            .replace("{{DISTANCE}}", "%.1f".format(state.calculatedDistance))
            .replace("{{FUEL_START}}", state.fuelAtStart)
            .replace("{{FUEL_REFUELED}}", state.fuelRefueled)
            .replace("{{FUEL_SPENT}}", "%.2f".format(state.fuelSpent))
            .replace("{{FUEL_END}}", "%.2f".format(state.fuelAtEnd))
            .replace("{{ROUTE_ROWS}}", routeRows)

        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "Путевой_лист_${state.dateString}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, filledHtml, "text/html", "UTF-8", null)
    }
}
