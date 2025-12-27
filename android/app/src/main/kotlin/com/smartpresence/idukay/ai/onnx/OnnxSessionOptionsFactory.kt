package com.smartpresence.idukay.ai.onnx

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

object OnnxSessionOptionsFactory {

    private val didLog = AtomicBoolean(false)

    fun create(tag: String): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val intraOpThreads = (cores / 2).coerceIn(1, 4)
        val interOpThreads = 1

        try {
            options.setIntraOpNumThreads(intraOpThreads)
        } catch (_: Throwable) {
        }
        try {
            options.setInterOpNumThreads(interOpThreads)
        } catch (_: Throwable) {
        }

        val provider = configureExecutionProvider(options)
        configureGraphOptimizations(options)

        if (didLog.compareAndSet(false, true)) {
            Timber.d(
                "ONNX Runtime ($tag): provider=$provider intraOp=$intraOpThreads interOp=$interOpThreads available=${availableProviders()}"
            )
        }

        return options
    }

    private fun configureExecutionProvider(options: OrtSession.SessionOptions): String {
        if (tryInvokeNoArg(options, "addNnapi")) {
            return "NNAPI"
        }
        if (tryInvokeNoArg(options, "addXnnpack")) {
            return "XNNPACK"
        }
        return "CPU"
    }

    private fun configureGraphOptimizations(options: OrtSession.SessionOptions) {
        try {
            val optLevelClass = Class.forName("ai.onnxruntime.OrtSession\$SessionOptions\$OptLevel")
            val allOpt = optLevelClass.enumConstants
                ?.firstOrNull { (it as? Enum<*>)?.name == "ALL_OPT" }
                ?: return
            val method = options.javaClass.getMethod("setOptimizationLevel", optLevelClass)
            method.invoke(options, allOpt)
        } catch (_: Throwable) {
        }
    }

    private fun tryInvokeNoArg(target: Any, methodName: String): Boolean {
        return try {
            val method = target.javaClass.getMethod(methodName)
            method.invoke(target)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun availableProviders(): String {
        return try {
            val env = OrtEnvironment.getEnvironment()
            val method = env.javaClass.getMethod("getAvailableProviders")
            method.invoke(env)?.toString() ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }
    }
    
    /**
     * Returns the current execution provider name for diagnostics
     */
    fun getProviderName(): String {
        return try {
            val options = OrtSession.SessionOptions()
            val provider = configureExecutionProvider(options)
            options.close()
            provider
        } catch (_: Throwable) {
            "CPU"
        }
    }
}
