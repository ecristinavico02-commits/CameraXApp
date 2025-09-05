

from flask import Flask, request, jsonify, send_from_directory
import pandas as pd
import numpy as np
from scipy.signal import butter, filtfilt
from scipy.fft import fft, fftfreq
import matplotlib.pyplot as plt
import os
import uuid 

'''
FUNCIONES
'''

def butter_bandpass_filter(data, lowcut, highcut, fs, order=4):
    nyq = 0.5 * fs
    low = lowcut / nyq
    high = highcut / nyq
    b, a = butter(order, [low, high], btype='band')
    y = filtfilt(b, a, data)
    return y

def calcular_y_dibujar_espectro(ax, df_sub, columna, fs, lowcut, highcut, label, titulo):
    
    signal = df_sub[columna].values
    
    # Filtrar la señal
    signal_filt = butter_bandpass_filter(signal, lowcut, highcut, fs)
    
    # FFT
    N = len(signal_filt)
    if N == 0:
        return 0, 0 # Si la señal estuviera vacía

    yf = fft(signal_filt)
    xf = fftfreq(N, 1/fs)[:N//2] # dt = 1/fs 
    magnitude = 2.0 / N * np.abs(yf[:N//2])

    idx_max = np.argmax(magnitude)
    frecuencia_dominante = xf[idx_max] #La frecuencia dominante es la frecuencia que más se repite
    rpm = frecuencia_dominante * 60

    
    ax.plot(xf, magnitude)
    ax.set_xlabel('Frecuencia (Hz)')
    ax.set_ylabel('Magnitud')
    ax.set_title(f'{titulo}')
    ax.grid(True)
    
    return frecuencia_dominante, rpm

def detectar_segmento_valido(df_input, ventana=1.0, umbral=3.0, min_estable=5.0, signal_column='Y_centrada'):
    df = df_input.copy() 
    time = df['Time (s)'].values
    signal = df[signal_column].values

    if len(time) < 2 or len(signal) < 2: 
        return time[0] if len(time) > 0 else 0, time[-1] if len(time) > 0 else 0

    dt = np.mean(np.diff(time))
    if dt == 0: 
        return time[0], time[-1]
        
    n_win = int(ventana / dt)
    n_consec = int(min_estable / dt)

    if n_win <= 0 or n_consec <=0 or n_win > len(signal): 
         return time[0], time[-1]


    stds = pd.Series(signal).rolling(n_win, center=True).std().fillna(method='bfill').fillna(method='ffill')
    std_mediana = np.median(stds[stds > 0]) 
    if std_mediana == 0: std_mediana = 1e-6 

    mask_valida = stds < umbral * std_mediana

    t_ini_val = time[0] 
    for i in range(len(mask_valida) - n_consec + 1):
        if mask_valida.iloc[i:i+n_consec].all():
            t_ini_val = time[i]
            break
    
    t_fin_val = time[-1] 
    for i in range(len(mask_valida), n_consec - 1, -1):
        if mask_valida.iloc[i-n_consec:i].all():
            t_fin_val = time[i-1] 
            break
    
    if t_fin_val < t_ini_val:
        t_fin_val = t_ini_val + (min_estable if (t_ini_val + min_estable) <= time[-1] else time[-1] - t_ini_val)
        if t_fin_val > time[-1]: t_fin_val = time[-1]

    return t_ini_val, t_fin_val


def generar_grafico_combinado(df_processed, t_inicio, t_fin, static_folder_path, app_config_static_folder):
   
    df_segment = df_processed[(df_processed['Time (s)'] >= t_inicio) & (df_processed['Time (s)'] <= t_fin)].copy()
    
    if df_segment.empty or len(df_segment['Time (s)'].unique()) < 2:
        
        print("Not enough data in the selected segment to generate graph.")
        return None 

    time_segment = df_segment['Time (s)'].values
    dt_segment = np.mean(np.diff(time_segment))
    if dt_segment == 0: 
        print("Cannot calculate sampling frequency (dt is zero).")
        return None
    fs_segment = 1 / dt_segment

    fig, axs = plt.subplots(2, 1, figsize=(10, 8)) 

    # Plot para el Eje X (HR)
    frec_x, rpm_x = calcular_y_dibujar_espectro(
        axs[0], df_segment, 'X_centrada', fs_segment, 0.8, 2.5, 'Espectro', 'Frecuencia cardiaca (HR)'
    )
    axs[0].annotate(f'Frecuencia dominante: {frec_x:.2f} Hz ({rpm_x:.1f} ppm)', xy=(0.05, 0.9), xycoords='axes fraction')


    # Plot para el Eje Y (BR)
    frec_y, rpm_y = calcular_y_dibujar_espectro(
        axs[1], df_segment, 'Y_centrada', fs_segment, 0.1, 0.5, 'Espectro', 'Frecuencia respiratoria (BR)'
    )
    axs[1].annotate(f'Frecuencia dominante: {frec_y:.2f} Hz ({rpm_y:.1f} rpm)', xy=(0.05, 0.9), xycoords='axes fraction')

    fig.suptitle('Espectro de frecuencia', fontsize=16)
    plt.tight_layout(rect=[0, 0, 1, 0.96]) 

    # Guardar la imagen combinada
    filename_combined = f"{uuid.uuid4()}.png"
    filepath_combined = os.path.join(static_folder_path, filename_combined)
    
    try:
        plt.savefig(filepath_combined)
        print(f"Combined graph saved to: {filepath_combined}")
    except Exception as e:
        print(f"Error saving combined graph: {e}")
        plt.close(fig)
        return None
    finally:
        plt.close(fig) 

    # Devolver la URL relativa para la app Android
    combined_graph_url = f"/{app_config_static_folder}/{filename_combined}"
    return combined_graph_url

'''
FLASK
'''

app = Flask(__name__)
STATIC_FOLDER_NAME = 'static_graphs' #El cliente envia las graficas a este fichero

BASE_DIR = os.path.abspath(os.path.dirname(__file__))
STATIC_FOLDER_PATH = os.path.join(BASE_DIR, STATIC_FOLDER_NAME)

app.config['STATIC_FOLDER_PATH'] = STATIC_FOLDER_PATH
app.config['STATIC_URL_PATH_COMPONENT'] = STATIC_FOLDER_NAME 

if not os.path.exists(app.config['STATIC_FOLDER_PATH']):
    os.makedirs(app.config['STATIC_FOLDER_PATH'])
    print(f"Created static folder at: {app.config['STATIC_FOLDER_PATH']}")

@app.route('/process_data_and_generate_combined_graph', methods=['POST'])
def handle_graph_request():
    if 'file' not in request.files:
        return jsonify({'error': 'No file part'}), 400
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400

    try:
        df = pd.read_csv(file)

        # 1. Normalización y Centrado 
        if 'Position X (px)' not in df.columns or 'Position Y (px)' not in df.columns or 'Time (s)' not in df.columns:
             return jsonify({'error': 'CSV must contain Time (s), Position X (px), and Position Y (px) columns'}), 400

        df['Position X (px) - norm'] = (df['Position X (px)'] - df['Position X (px)'].min()) / (df['Position X (px)'].max() - df['Position X (px)'].min())
        df['Position Y (px) - norm'] = (df['Position Y (px)'] - df['Position Y (px)'].min()) / (df['Position Y (px)'].max() - df['Position Y (px)'].min())
        
        df['X_centrada'] = df['Position X (px) - norm'] - df['Position X (px) - norm'].iloc[0]
        df['X_centrada'] -= df['X_centrada'].mean()
        df['Y_centrada'] = df['Position Y (px) - norm'] - df['Position Y (px) - norm'].iloc[0]
        df['Y_centrada'] -= df['Y_centrada'].mean()

        # 2. Detectar segmento válido
        
        if len(df) < 10: 
            return jsonify({'error': 'Not enough data in CSV for processing after initial load'}), 400

        t_ini, t_fin = detectar_segmento_valido(df, signal_column='Y_centrada') 
        
        print(f"Segmento válido detectado: t_inicio={t_ini}, t_fin={t_fin}")
        
        if t_ini is None or t_fin is None or t_fin <= t_ini:
            print("No se pudo detectar un segmento válido o el segmento es inválido.")
            
            if len(df) > 20: 
                 t_ini = df['Time (s)'].iloc[0]
                 t_fin = df['Time (s)'].iloc[min(19, len(df)-1)] 
                 if t_fin <= t_ini: 
                      return jsonify({'error': 'Not enough data for fallback segment processing'}), 400
                 print(f"Fallback: Usando t_inicio={t_ini}, t_fin={t_fin}")
            else:
                 return jsonify({'error': 'No valid segment found and not enough data for fallback'}), 400


        # 3. Generar la imagen del gráfico combinado
        combined_graph_url = generar_grafico_combinado(
            df, t_ini, t_fin, 
            app.config['STATIC_FOLDER_PATH'], #Para guardado
            app.config['STATIC_URL_PATH_COMPONENT'] #URL
        )

        if combined_graph_url:
            return jsonify({
                'message': 'Combined graph generated successfully',
                'combined_graph_image_url': combined_graph_url, 
                'error': None
            })
        else:
            return jsonify({'error': 'Failed to generate combined graph image'}), 500

    except Exception as e:
        app.logger.error(f"Error processing file for combined graph: {e}")
        import traceback
        traceback.print_exc() 
        return jsonify({'error': str(e)}), 500

# Endpoint 

@app.route(f"/{STATIC_FOLDER_NAME}/<filename>")
def serve_static_graph(filename):
    return send_from_directory(app.config['STATIC_FOLDER_PATH'], filename)

if __name__ == '__main__':
    print(f"Graph images will be saved in: {app.config['STATIC_FOLDER_PATH']}")
    print(f"Graphs will be served from URL path prefix: /{app.config['STATIC_URL_PATH_COMPONENT']}")
    app.run(debug=True, host='0.0.0.0', port=5000)

