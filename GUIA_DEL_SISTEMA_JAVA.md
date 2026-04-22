# Guía Integral del Sistema de Gestión Docente (Versión Java)

Esta guía te servirá de manual permanente cuando necesites iniciar tu servidor, hacer modificaciones, generar tu versión de producción o sencillamente entender cómo están conectados los archivos de tu sistema ahora que completamos la migración a Java (Spring Boot).

---

## 🚀 1. ¿Cómo iniciar el servidor local para hacer pruebas? (Cuando IA no esté)

Como descubrimos, tu entorno Windows no tiene configurado el comando global `mvn`. Pero no te preocupes, tienes el programa original descargado en la carpeta `apache-maven-3.9.6`. Este es el paso exacto que debes dar siempre que desees encender tu app:

**Paso a paso:**
1. Abre **PowerShell** o **CMD**.
2. Dirígete a la carpeta base del código Java ingresando el siguiente comando:
   ```powershell
   cd C:\temp\gestion-docente-web\backend
   ```
3. Ejecuta el archivo que enciende el servidor invocando su ruta completa. Copia y pega esto en la consola y dale Enter:
   ```powershell
   ..\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
   ```
4. Espera unos segundos a que en la consola se impriman muchos mensajes. Cuando veas el mensaje final **`Started GestionDocenteApplication`**, significa que el servidor está 100% encendido.
5. Abre Google Chrome o Microsoft Edge, y entra a `http://localhost:8091/`. 

*(Para apagarlo y dejar de hacer pruebas, solo presiona `Ctrl + C` en tu consola).*

---

## 📦 2. ¿Cómo construir el sistema instalable (WAR) para Producción?

Cuando termines tu etapa de desarrollo y necesites el archivo empaquetado final para mandarlo a la Máquina Virtual (Apache Tomcat), harás esto:

1. Abre tu consola y navega a `C:\temp\gestion-docente-web\backend`.
2. Escribe el comando que ejecuta la construcción:
   ```powershell
   ..\apache-maven-3.9.6\bin\mvn.cmd clean package
   ```
3. Espera a que termine. En la consola dirá **`BUILD SUCCESS`**.
4. ¡Listo! Abre el explorador de Windows, ve a `C:\temp\gestion-docente-web\backend\target\` y ahí encontrarás un archivo llamado `gestion-docente-0.0.1-SNAPSHOT.war`. Ese archivo se sube directo al Apache Tomcat.

---

## 🗺️ 3. Mapa de Archivos (Qué hace cada documento del sistema)

Todo tu sistema ahora habita dentro de `C:\temp\gestion-docente-web\backend\`. Ya puedes considerar obsoletos/borrar de la raíz el viejo script `server.ps1` y su ejecutable `build_war.ps1`.

### A. La Interfaz Visual (Frontend)
📁 **Ubicación:** `backend\src\main\resources\static\`
- **`index.html`**: Estructura general, botones, modales y formularios que ve el usuario.
- **`app.js`**: Reemplazo de interactividad; manda alertas y lee los "clicks". Se conecta directamente con el código Java con métodos HTTP (GET/POST/PUT).
- **`index.css`**: Define todos los colores oscuros, sombras e identidades del portal militar.
- **`*.png`**: Los logotipos que adornan la plataforma.
*Nota*: Cuando alteras la vista, lo harás en estos archivos. 

### B. El Motor de Configuración y Bases de datos (Propiedades)
📁 **Ubicación:** `backend\src\main\resources\`
- **`application.properties`**: Es la central de control de servidor. Aquí va el usuario/contraseña de PostgreSQL, el puerto (8091) y las limitantes de tamaño máximo de los PDF permitidos.

### C. La Lógica Oculta (El Backend en Java)
📁 **Ubicación:** `backend\src\main\java\com\emi\gestiondocente\`

Estos archivos de Java son la capa inteligente que interactúa entre la base de datos PostgreSQL y la Web (`index.html`).

- **`GestionDocenteApplication.java`**: El corazón y punto de entrada. Es quien inicializa e inyecta toda la red de servidores a tu red.
- **`config/WebConfig.java`**: Nuestro puente para los archivos físicos. Modificar esto es vital únicamente si el día de mañana tus Máquinas Virtuales cambian las fotografías o contratos de disco duro. Ahorita le indica a tu programa que las fotografías o archivos se leen en "C:\temp\gestion-docente-web\...".

**Los Controladores:**
Cada clase atiende un grupo muy exacto de tareas. Si el código HTML trata de enviar un registro de profesor a `/api/docentes`, el encargado de atraparlo será su respectivo controlador en Java.
- **`DocenteController.java`**: Busca en la BD la lista de mentores, registra nuevos o enlista docentes por ID.
- **`ExpedienteController.java` / `FotoCedulaController.java`**: Especializados en procesar binarios, extraer documentos provenientes del cliente y volcarlos directamente al disco duro a sus carpetas respectivas (ej. fotos, expedientes o docx temporales).
- **`MateriaController.java`**: Consulta de materias y especialidades adjuntas.
- **`ContratoController.java`**: Conecta la información del personal y lo unifica insertando variables en una plantilla física de Microsoft Word (Apache POI).
- **`AuthController.java`**: Quien evalúa el login para conceder o denegar el permiso con su sistema de encriptación de claves seguro (hash bcrypt).
