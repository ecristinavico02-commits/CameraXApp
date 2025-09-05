import pandas as pd
import matplotlib.pyplot as plt
from scipy.fft import fft, fftfreq
from scipy.signal import butter, filtfilt
import numpy as np



def butter_bandpass_filter(data, lowcut, highcut, fs, order=4):
    nyq = 0.5 * fs
    low = lowcut / nyq
    high = highcut / nyq
    b, a = butter(order, [low, high], btype='band')
    y = filtfilt(b, a, data)
    return y

def calcular_frecuencia(df, columna,t_inicio, t_fin, lowcut, highcut, label, titulo):
    df_sub = df[(df['Time (s)'] >= t_inicio) & (df['Time (s)'] <= t_fin)].copy()
    signal = df_sub[columna].values
    time = df_sub['Time (s)'].values
    # Calcular frecuencia de muestreo
    dt = np.mean(np.diff(time))
    fs = 1 / dt
    # Filtrar la señal
    signal_filt = butter_bandpass_filter(signal, lowcut, highcut, fs)
    # FFT
    N = len(signal_filt)
    yf = fft(signal_filt)
    xf = fftfreq(N, dt)[:N//2]

    magnitude = 2.0 / N * np.abs(yf[:N//2])

    idx_max = np.argmax(magnitude)
    frecuencia_dominante = xf[idx_max]
    rpm = frecuencia_dominante * 60
    plt.figure(figsize=(10, 4))
    plt.plot(xf, magnitude)
    plt.xlabel('Frecuencia (Hz)')
    plt.ylabel('Magnitud')
    plt.title(f'{titulo} {label}')
    plt.grid(True)
    plt.text(0.02, 0.95,
            f'Frecuencia dominante: {frecuencia_dominante:.2f} Hz ({rpm:.1f} ppm)',
            transform=plt.gca().transAxes,
            ha='left', va='top',
            fontsize=10)

    plt.tight_layout()
    plt.show()
    plt.tight_layout()
    plt.show()
    return frecuencia_dominante, rpm

def espectro_completo(df, columna, t_inicio, t_fin, label, titulo):
    df_sub = df[(df['Time (s)'] >= t_inicio) & (df['Time (s)'] <= t_fin)].copy()
    signal = df_sub[columna].values
    time = df_sub['Time (s)'].values

    dt = np.mean(np.diff(time))
    fs = 1 / dt


    N = len(signal)
    yf = fft(signal)
    xf = fftfreq(N, dt)

    magnitude = 2.0 / N * np.abs(yf)

   
    plt.figure(figsize=(12, 5))
    plt.plot(xf, magnitude)
    plt.xlabel('Frecuencia (Hz)')
    plt.ylabel('Magnitud')
    plt.title(f'Espectro completo - {titulo}, {label}')
    plt.grid(True)
    plt.tight_layout()
    plt.show()
    
def detectar_segmento_valido(df, ventana=1.0, umbral=3.0, min_estable=5.0):
    
    time = df['Time (s)'].values
    signal = df['Y_centrada'].values
    dt = np.mean(np.diff(time))
    n_win = int(ventana / dt)
    n_consec = int(min_estable / dt)

    # Calcular desviación estándar en ventana móvil
    stds = pd.Series(signal).rolling(n_win, center=True).std().fillna(method='bfill').fillna(method='ffill')

    std_mediana = np.median(stds)
    mask_valida = stds < umbral * std_mediana

    # Buscar desde el inicio: primer bloque estable de al menos min_estable s
  
    t_ini = None
    for i in range(len(mask_valida) - n_consec):
        if mask_valida.iloc[i:i+n_consec].all():
            t_ini = time[i]
            break

    # Buscar inicio estable
    t_ini = None
    for i in range(len(mask_valida) - n_consec):
        if mask_valida.iloc[i:i+n_consec].all():
            t_ini = time[i]
            break
    
    # Buscar fin estable
    t_fin = None
    for i in range(len(mask_valida) - n_consec, 0, -1):
        if mask_valida.iloc[i-n_consec:i].all():
            t_fin = time[i]
            break

    return t_ini, t_fin


#Lectura de archivos
df_normal = pd.read_csv('recording_2025-09-02_19-42-39.csv')


# Normalización min-max para X y Y

df_normal['Position X (px) - norm'] = (df_normal['Position X (px)'] - df_normal['Position X (px)'].min()) / (df_normal['Position X (px)'].max() - df_normal['Position X (px)'].min())
df_normal['Position Y (px) - norm'] = (df_normal['Position Y (px)'] - df_normal['Position Y (px)'].min()) / (df_normal['Position Y (px)'].max() - df_normal['Position Y (px)'].min())

# Centrado en 0

df_normal['X_centrada'] = df_normal['Position X (px) - norm'] - df_normal['Position X (px) - norm'].iloc[0] 
df_normal['X_centrada'] -= df_normal['X_centrada'].mean()

df_normal['Y_centrada'] = df_normal['Position Y (px) - norm'] - df_normal['Position Y (px) - norm'].iloc[0] 
df_normal['Y_centrada'] -= df_normal['Y_centrada'].mean()

# Filtrar datos 

# t_ini_normal, t_fin_normal = detectar_segmento_valido(df_normal)

df_normal = df_normal[(df_normal['Time (s)'] >= 3) & (df_normal['Time (s)'] <= 29)]

# Eje X

plt.figure(figsize=(10, 4))
plt.plot(df_normal['Time (s)'], df_normal['X_centrada'])
plt.xlabel('Tiempo (s)')
plt.ylabel('Eje X (px)')
plt.title('Respiración tras un intenso ejercicio')
plt.grid(True)
plt.legend()
plt.tight_layout()
plt.show()

# Eje Y

plt.figure(figsize=(10, 4))
plt.plot(df_normal['Time (s)'], df_normal['Y_centrada'], color='orange')
plt.xlabel('Tiempo (s)')
plt.ylabel('Eje Y (px)')
plt.title('Respiración tras un intenso ejercicio')
plt.grid(True)
plt.legend()
plt.tight_layout()
plt.show()

# FFT

espectro_normal_X = espectro_completo(df_normal, 'X_centrada', 15, 40, 'Respirando normal', 'Eje X')
espectro_normal_Y = espectro_completo(df_normal, 'Y_centrada', 15, 40, 'Respirando normal', 'Eje Y')
frecuencia_normal_X, rpm_normal_X = calcular_frecuencia(df_normal, 'X_centrada', 15, 40, 0.8, 2.5, 'Frecuencia cardiaca (HR)', '')
frecuencia_normal_Y, rpm_normal_Y = calcular_frecuencia(df_normal, 'Y_centrada', 15, 40, 0.1, 0.5, 'Frecuencia respiratoria (BR)', '')






