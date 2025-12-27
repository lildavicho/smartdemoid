"""
Script alternativo para cuantizar modelos ONNX usando solo la librería onnx
Compatible con Python 3.12+
"""

import onnx
from onnx import numpy_helper
import numpy as np
import os

def quantize_weights_to_int8(model_path, output_path):
    """
    Cuantiza los pesos de un modelo ONNX de FP32 a INT8
    Método simplificado sin onnxruntime
    """
    print(f"📦 Cargando modelo: {model_path}")
    
    # Cargar modelo
    model = onnx.load(model_path)
    
    # Obtener tamaño original
    original_size = os.path.getsize(model_path) / (1024 * 1024)
    
    print(f"🔄 Cuantizando pesos a INT8...")
    
    # Cuantizar cada inicializador (peso)
    for initializer in model.graph.initializer:
        # Obtener tensor como numpy array
        tensor = numpy_helper.to_array(initializer)
        
        # Solo cuantizar tensores float32
        if tensor.dtype == np.float32:
            # Calcular escala y zero point
            min_val = float(tensor.min())
            max_val = float(tensor.max())
            
            # Evitar división por cero
            if max_val == min_val:
                continue
                
            scale = (max_val - min_val) / 255.0
            zero_point = int(-min_val / scale)
            
            # Cuantizar a uint8
            quantized = np.round((tensor / scale) + zero_point).astype(np.uint8)
            
            # Actualizar inicializador
            initializer.ClearField('float_data')
            initializer.ClearField('raw_data')
            initializer.data_type = onnx.TensorProto.UINT8
            initializer.raw_data = quantized.tobytes()
    
    # Guardar modelo cuantizado
    print(f"💾 Guardando modelo cuantizado: {output_path}")
    onnx.save(model, output_path)
    
    # Mostrar estadísticas
    quantized_size = os.path.getsize(output_path) / (1024 * 1024)
    reduction = ((original_size - quantized_size) / original_size) * 100
    
    print(f"✅ Cuantizado exitosamente!")
    print(f"   Original:   {original_size:.2f} MB")
    print(f"   Cuantizado: {quantized_size:.2f} MB")
    print(f"   Reducción:  {reduction:.1f}%")
    print()
    
    return True

def main():
    print("🚀 Cuantización de modelos ONNX (Método Simplificado)\n")
    print("⚠️  NOTA: Este método cuantiza solo los pesos.")
    print("   Para mejor rendimiento, considera usar onnxruntime con Python 3.11\n")
    
    models = [
        {
            'input': 'android/app/src/main/assets/models/scrfd_10g_bnkps.onnx',
            'output': 'android/app/src/main/assets/models/scrfd_10g_bnkps_int8.onnx',
            'name': 'SCRFD Detector'
        },
        {
            'input': 'android/app/src/main/assets/models/w600k_r50.onnx',
            'output': 'android/app/src/main/assets/models/w600k_r50_int8.onnx',
            'name': 'ArcFace Recognizer'
        }
    ]
    
    success_count = 0
    for model in models:
        print(f"🔄 Procesando: {model['name']}")
        try:
            if os.path.exists(model['input']):
                quantize_weights_to_int8(model['input'], model['output'])
                success_count += 1
            else:
                print(f"❌ No se encontró: {model['input']}\n")
        except Exception as e:
            print(f"❌ Error: {e}\n")
    
    print(f"\n{'='*60}")
    print(f"✅ Completado: {success_count}/{len(models)} modelos cuantizados")
    print(f"{'='*60}")
    
    if success_count > 0:
        print("\n📝 Próximos pasos:")
        print("1. Actualizar RecognitionConfig.kt:")
        print('   DETECTOR_MODEL = "scrfd_10g_bnkps_int8.onnx"')
        print('   RECOGNIZER_MODEL = "w600k_r50_int8.onnx"')
        print("2. Rebuild proyecto Android")
        print("3. Probar reconocimiento facial")

if __name__ == "__main__":
    main()
