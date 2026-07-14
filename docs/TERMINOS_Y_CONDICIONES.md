# Términos y Condiciones de Uso y Política de Privacidad (VitalFi)

**Fecha de última actualización:** 14 de julio de 2026

Bienvenido a **VitalFi**, un proyecto de investigación y desarrollo de software libre liderado por **Carlos Mundaray / Solvitco**. 

El presente documento establece los **Términos y Condiciones de Uso** y la **Política de Privacidad** que rigen el acceso y uso de la aplicación móvil VitalFi, sus herramientas complementarias en Python, sus repositorios de código y la integración con la plataforma de identificación de investigadores **ORCID** (https://orcid.org/).

Al descargar, instalar, compilar o utilizar cualquier parte de este software, o al autenticarse mediante su cuenta de ORCID en nuestros servicios relacionados, usted acepta de manera expresa y sin reservas los términos descritos a continuación. Si no está de acuerdo con alguna de estas condiciones, le rogamos que no utilice el software ni asocie sus credenciales.

---

## 1. Naturaleza del Proyecto y Exención de Responsabilidad Crítica

> [!WARNING]
> **AVISO IMPORTANTE: HERRAMIENTA EXPERIMENTAL Y DE INVESTIGACIÓN**
> 
> * **Uso Exclusivamente Experimental:** VitalFi es una prueba de concepto científica y una herramienta de investigación diseñada para analizar la viabilidad de la detección de respiración bajo escombros mediante variaciones en las señales Wi-Fi (RSSI) y triangulación magnética.
> * **Sin Garantía de Precisión:** Los resultados, alertas, estimaciones y representaciones visuales generados por la aplicación (radar 2D/3D) son meras estimaciones matemáticas sujetas a interferencias, ruido electromagnético y limitaciones físicas de los sensores de los dispositivos comerciales. **No son 100% confiables.**
> * **No Reemplaza Equipos Profesionales:** VitalFi **no reemplaza** bajo ninguna circunstancia a los equipos profesionales de búsqueda y rescate (perros de rescate, geófonos, cámaras térmicas, etc.), ni a los protocolos oficiales de emergencia establecidos por las autoridades de protección civil o cuerpos médicos.
> * **Exclusión de Responsabilidad por Daños:** En la medida máxima permitida por la ley aplicable, el autor (Carlos Mundaray), Solvitco, sus colaboradores y las entidades asociadas no serán responsables de ninguna pérdida, lesión, fallecimiento, daño directo o indirecto, o falla operativa derivada de la confianza depositada en las indicaciones de este software durante situaciones de emergencia reales. El uso del software se realiza bajo el propio riesgo y responsabilidad del usuario.

---

## 2. Integración con ORCID (Open Researcher and Contributor ID)

Para promover la transparencia científica, la atribución de contribuciones de investigación y la validación académica, VitalFi permite a los investigadores y desarrolladores vincular su identidad digital de **ORCID** (https://orcid.org/).

### 2.1. Alcance de la Integración y Permisos de API
Cuando usted utiliza la opción de autenticación o vinculación con ORCID en nuestras plataformas o herramientas, solicitamos acceso a través del protocolo OAuth 2.0 de ORCID bajo los siguientes parámetros:
* **Lectura de Información Pública (Access Public Info):** Obtenemos su **ORCID iD** único, su nombre, apellido y otros datos que usted haya configurado como públicos en su registro de ORCID.
* **Uso del Identificador:** Su ORCID iD se utiliza exclusivamente para:
  1. Identificar y acreditar sus contribuciones como investigador, desarrollador o validador científico del proyecto VitalFi.
  2. Mostrar su autoría o participación académica en listados públicos relacionados con la validación del modelo o artículos científicos asociados.
  3. Vincular publicaciones académicas derivadas de las pruebas de software con su perfil de investigador.

### 2.2. Gestión de Credenciales y Seguridad
* **No Almacenamos Contraseñas:** El proceso de inicio de sesión se realiza directamente en los servidores seguros de ORCID. VitalFi nunca tiene acceso, no lee ni almacena sus contraseñas o datos de acceso a ORCID.
* **Tokens de Acceso:** Almacenamos únicamente tokens de acceso temporales proporcionados por la API de ORCID para realizar las consultas autorizadas. Estos tokens se gestionan bajo estándares de seguridad informática para evitar su acceso no autorizado.
* **Revocación de Acceso:** Usted puede revocar los permisos otorgados a la aplicación VitalFi en cualquier momento directamente desde la configuración de su cuenta en el sitio web oficial de ORCID (sección *Trusted organizations* o *Aplicaciones de confianza*). Al hacerlo, la integración dejará de funcionar inmediatamente.

---

## 3. Política de Privacidad y Tratamiento de Datos

Nos tomamos muy en serio la privacidad de quienes utilizan nuestro software y colaboran en la investigación científica.

### 3.1. Datos Recolectados por la Aplicación Android (VitalFi)
* **Permiso de Ubicación (Location Permission):** La aplicación requiere acceso a la ubicación del dispositivo en Android. Esto se debe estrictamente a restricciones del sistema operativo Android, que exige este permiso para realizar escaneos de redes Wi-Fi (acceso a señales RSSI). **VitalFi no registra, no almacena ni transmite sus coordenadas GPS a servidores externos.** El procesamiento del rumbo magnético y las señales Wi-Fi se realiza de forma local en el dispositivo.
* **Datos Técnicos de Red (Wi-Fi RSSI):** Las intensidades de señal de los routers y puntos de acceso Wi-Fi cercanos se procesan localmente y en tiempo real con fines matemáticos y de filtrado digital (Filtro Hampel, FFT). Estos datos no se asocian con su identidad personal.
* **Datos del Sensor (Magnetómetro):** Las lecturas del magnetómetro se usan localmente para orientar el radar. No se recopilan ni transmiten de forma remota.

### 3.2. Datos Compartidos con Terceros
* No vendemos, alquilamos ni comercializamos datos de usuarios con terceros.
* Los únicos datos compartidos son aquellos autorizados expresamente por el usuario al realizar la autenticación con ORCID, de acuerdo con los flujos de la API pública de ORCID.

### 3.3. Base Legal y Cumplimiento (GDPR / LOPD)
El tratamiento de sus datos de ORCID y la información académica asociada se basa en su **consentimiento explícito** al iniciar la vinculación. Usted tiene derecho a solicitar la eliminación de cualquier registro académico que le asocie con este proyecto en nuestros servidores enviando una solicitud por correo electrónico.

---

## 4. Propiedad Intelectual y Licenciamiento

### 4.1. Licencia de Código Abierto
El código fuente de VitalFi se distribuye bajo la **Licencia GNU GPL v3 (General Public License v3)**. Esto significa que usted es libre de usar, copiar, modificar, distribuir y compartir el software, siempre y cuando se conserven los avisos de derechos de autor y se libere cualquier modificación del código fuente bajo esta misma licencia (GPL v3).

### 4.2. Derechos de Autor
* **Copyright © 2026 Carlos Mundaray — Solvitco (https://solvitco.com)**.
* Las marcas, logotipos y diseños visuales de VitalFi y Solvitco son propiedad exclusiva de sus creadores y no están cubiertos por la licencia GPL v3 de código abierto sin consentimiento previo por escrito.

---

## 5. Modificaciones a los Términos

Nos reservamos el derecho de modificar o actualizar estos términos de uso y políticas de privacidad en cualquier momento para adaptarlos a cambios legislativos, mejoras del software o actualizaciones en las políticas de la API de ORCID. Le recomendamos revisar este documento periódicamente. El uso continuado del software tras la publicación de cambios implica la aceptación de los nuevos términos.

---

## 6. Contacto y Soporte

Si tiene preguntas sobre estos términos, la integración con ORCID o el tratamiento de sus datos personales en este proyecto de investigación, puede ponerse en contacto con nosotros a través de:

* **Desarrollador Principal:** Carlos Mundaray
* **Sitio Web Oficial:** [Solvitco](https://solvitco.com)
* **Repositorio de Código:** [GitHub - VitalFi](https://github.com/carlosmundaray/vitalfi) (o el canal de soporte especificado en el repositorio).
