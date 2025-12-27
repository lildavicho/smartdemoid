package com.smartpresence.idukay.di

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import com.smartpresence.idukay.ai.detector.SCRFDDetector
import com.smartpresence.idukay.ai.pipeline.RecognitionPipeline
import com.smartpresence.idukay.ai.recognizer.ArcFaceRecognizer
import com.smartpresence.idukay.ai.tracker.FaceTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIModule {
    
    @Provides
    @Singleton
    fun provideOrtEnvironment(): OrtEnvironment {
        return OrtEnvironment.getEnvironment()
    }
    
    @Provides
    @Singleton
    fun provideFaceDetector(
        @ApplicationContext context: Context,
        ortEnv: OrtEnvironment
    ): SCRFDDetector {
        return SCRFDDetector(context, ortEnv)
    }
    
    @Provides
    @Singleton
    fun provideFaceRecognizer(
        @ApplicationContext context: Context,
        ortEnv: OrtEnvironment
    ): ArcFaceRecognizer {
        return ArcFaceRecognizer(context, ortEnv)
    }
    
    @Provides
    @Singleton
    fun provideFaceTracker(): FaceTracker {
        return FaceTracker()
    }
    
    @Provides
    @Singleton
    fun provideRecognitionPipeline(
        detector: SCRFDDetector,
        recognizer: ArcFaceRecognizer,
        tracker: FaceTracker
    ): RecognitionPipeline {
        return RecognitionPipeline(detector, recognizer, tracker)
    }
}
