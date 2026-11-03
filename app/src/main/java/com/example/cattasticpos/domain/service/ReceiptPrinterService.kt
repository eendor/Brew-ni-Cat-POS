package com.example.cattasticpos.domain.service

import com.example.cattasticpos.domain.model.SplitPayment
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.example.cattasticpos.domain.model.CartLineSelection
import com.example.cattasticpos.domain.model.Order
import com.example.cattasticpos.domain.model.ZReadingSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build

class ReceiptPrinterService(private val context: Context) {
    private val standardSerialPortServiceId: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    suspend fun printReceipt(order: Order): Result<Unit> = withContext(Dispatchers.IO) {
        printEscPos { outputStream ->
            writeReceipt(outputStream, order)
        }
    }

    suspend fun printZReading(summary: ZReadingSummary): Result<Unit> = withContext(Dispatchers.IO) {
        printEscPos { outputStream ->
            writeZReading(outputStream, summary)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun printEscPos(block: (OutputStream) -> Unit): Result<Unit> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return Result.failure(Exception("Bluetooth connect permission is required."))
                }
            }
            val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                return Result.failure(Exception("Bluetooth is disabled or not supported."))
            }

            val pairedDevices: Set<BluetoothDevice>? = try {
                adapter.bondedDevices
            } catch (e: SecurityException) {
                return Result.failure(Exception("SecurityException: Bluetooth connect permission is missing."))
            }

            if (pairedDevices.isNullOrEmpty()) {
                return Result.failure(Exception("No paired Bluetooth devices found."))
            }

            val printerDevice = pairedDevices.firstOrNull {
                val name = try {
                    it.name?.lowercase() ?: ""
                } catch (e: SecurityException) {
                    ""
                }
                name.contains("printer") || name.contains("pos") || name.contains("pt-")
            } ?: return Result.failure(Exception("No paired receipt printer found."))

            var socket: BluetoothSocket? = null
            try {
                socket = printerDevice.createRfcommSocketToServiceRecord(standardSerialPortServiceId)
                withTimeout(3_000) {
                    socket.connect()
                }
                block(socket.outputStream)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try {
                    socket?.close()
                } catch (_: IOException) {
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeReceipt(outputStream: OutputStream, order: Order) {
        val ESC = 0x1B.toByte()
        val GS = 0x1D.toByte()
        val initPrinter = byteArrayOf(ESC, '@'.code.toByte())
        val alignCenter = byteArrayOf(ESC, 'a'.code.toByte(), 1)
        val alignLeft = byteArrayOf(ESC, 'a'.code.toByte(), 0)
        val boldOn = byteArrayOf(ESC, 'E'.code.toByte(), 1)
        val boldOff = byteArrayOf(ESC, 'E'.code.toByte(), 0)
        val sizeDouble = byteArrayOf(GS, '!'.code.toByte(), 0x11) // double width + double height
        val sizeNormal = byteArrayOf(GS, '!'.code.toByte(), 0x00)
        val cutPaper = byteArrayOf(GS, 'V'.code.toByte(), 66, 0)

        outputStream.write(initPrinter)
        outputStream.write(alignCenter)
        outputStream.write(boldOn)
        outputStream.write("Brew ni Cat\n".toByteArray())
        outputStream.write(boldOff)
        // Table / customer name printed BIG so servers immediately see where to bring the order.
        if (!order.tableLabel.isNullOrBlank()) {
            outputStream.write("\n".toByteArray())
            outputStream.write(boldOn)
            outputStream.write(sizeDouble)
            outputStream.write("${order.tableLabel}\n".toByteArray())
            outputStream.write(sizeNormal)
            outputStream.write(boldOff)
        }
        outputStream.write("Order: #${order.receiptNumber}\n".toByteArray())
        outputStream.write("Payment: ${SplitPayment.describe(order.paymentMethod, order.paymentReference).replace("₱", "PHP ")}\n".toByteArray())
        outputStream.write("--------------------------------\n".toByteArray())

        outputStream.write(alignLeft)
        order.items.forEach { item ->
            val line = "${item.quantity}x ${item.itemName} (${item.variantName})\n"
            outputStream.write(line.toByteArray())
            // Flavor and extras print on their own lines. The stored string now marks extras with
            // a "+", so printing it raw would read "- + Egg (Sunny Side Up)".
            val selection = CartLineSelection.parse(item.flavor, item.itemId)
            selection.baseFlavor?.let { outputStream.write("   - $it\n".toByteArray()) }
            selection.coffeeOption?.let { outputStream.write("   - $it\n".toByteArray()) }
            if (selection.addOnLabels.isNotEmpty()) {
                outputStream.write("   + ${selection.addOnLabels.joinToString(", ")}\n".toByteArray())
            }
            outputStream.write("   PHP ${item.totalPrice}\n".toByteArray())
        }
        outputStream.write("--------------------------------\n".toByteArray())

        outputStream.write(alignCenter)
        outputStream.write("Subtotal: PHP ${order.subtotal}\n".toByteArray())
        outputStream.write("Discount: -PHP ${order.discountDeduction}\n".toByteArray())
        outputStream.write(boldOn)
        outputStream.write("Total: PHP ${order.total}\n".toByteArray())
        outputStream.write(boldOff)
        outputStream.write("\nThank you, meow!\n\n\n\n".toByteArray())
        outputStream.write(cutPaper)
        outputStream.flush()
    }

    private fun writeZReading(outputStream: OutputStream, summary: ZReadingSummary) {
        val ESC = 0x1B.toByte()
        val GS = 0x1D.toByte()
        val initPrinter = byteArrayOf(ESC, '@'.code.toByte())
        val alignCenter = byteArrayOf(ESC, 'a'.code.toByte(), 1)
        val alignLeft = byteArrayOf(ESC, 'a'.code.toByte(), 0)
        val boldOn = byteArrayOf(ESC, 'E'.code.toByte(), 1)
        val boldOff = byteArrayOf(ESC, 'E'.code.toByte(), 0)
        val cutPaper = byteArrayOf(GS, 'V'.code.toByte(), 66, 0)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        outputStream.write(initPrinter)
        outputStream.write(alignCenter)
        outputStream.write(boldOn)
        outputStream.write("Z-READING REPORT\n".toByteArray())
        outputStream.write(boldOff)
        outputStream.write("Brew ni Cat\n".toByteArray())
        outputStream.write("$timestamp\n".toByteArray())
        outputStream.write("--------------------------------\n".toByteArray())
        outputStream.write(alignLeft)
        outputStream.write("Orders: ${summary.orderCount}\n".toByteArray())
        outputStream.write("Gross Sales: PHP ${summary.grossSales}\n".toByteArray())
        outputStream.write("Discounts: -PHP ${summary.discounts}\n".toByteArray())
        outputStream.write("Net Revenue: PHP ${summary.netRevenue}\n".toByteArray())
        outputStream.write("Cash Sales: PHP ${summary.cashSales}\n".toByteArray())
        outputStream.write("GCash Sales: PHP ${summary.gcashSales}\n".toByteArray())
        outputStream.write("Expenses: -PHP ${summary.totalExpenses}\n".toByteArray())
        outputStream.write("Profits: PHP ${summary.profits}\n".toByteArray())
        outputStream.write("Cash Drawer: PHP ${summary.cashDrawer}\n".toByteArray())
        outputStream.write("Starting Float: PHP ${summary.startingCashFloat}\n".toByteArray())
        summary.topSellingItem?.let {
            outputStream.write("Best Seller: ${it.first} (${it.second})\n".toByteArray())
        }
        outputStream.write(alignCenter)
        outputStream.write("\nEnd of Z-Reading\n\n\n\n".toByteArray())
        outputStream.write(cutPaper)
        outputStream.flush()
    }
}
