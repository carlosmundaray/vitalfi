# Cómo añadir VitalFi en ORCID (sin error de formulario)

Si ORCID muestra: **"Revise el formulario y solucione los problemas antes de guardarlo"**, usa este método.

## Opción A — Añadir manualmente (recomendada)

1. Entra a https://orcid.org/my-orcid  
2. En **Works** → **Add** → **Add manually**  
3. Completa **solo** estos campos (deja el resto vacío si molesta):

| Campo ORCID | Valor exacto |
|-------------|--------------|
| **Work type** | `Software` |
| **Title** | `VitalFi: Wi-Fi-based life detection under rubble` |
| **Publication date** | Year `2026` (mes/día opcionales) |
| **Work identifiers → Type** | `URI` o `URL` |
| **Work identifiers → Value** | `https://github.com/carlosmundaray/vitalfi_flutter` |
| **URL / Link to work** (si aparece) | `https://github.com/carlosmundaray/vitalfi_flutter` |
| **Citation type** (si aparece) | `BibTeX` |
| **Citation** (si aparece) | pega el contenido de `vitalfi.bib` |

4. **Visibility:** Public  
5. **Save**

### Errores frecuentes
- Título con guión largo especial (`–`) → usa guión normal `-`
- Identificador vacío o sin `https://` → ORCID no guarda
- Tipo de trabajo distinto de **Software**
- Pegar BibTeX con `@software`, `license` u `orcid` → el importador falla

## Opción B — Importar BibTeX

1. Usa el archivo del repo: [`vitalfi.bib`](vitalfi.bib) (formato `@misc`, compatible con ORCID).  
2. Works → **Add** → **Import BibTeX** → selecciona `vitalfi.bib`.  
3. Revisa el tipo de trabajo: si queda como *Other*, cámbialo a **Software** y guarda.

## Después (mejor para ORCID)

1. Archiva el repo en [Zenodo](https://zenodo.org) (GitHub → Release `v1.3.20`).  
2. Copia el **DOI**.  
3. En ORCID: **Add** → **Add DOI** → pega el DOI (menos errores que el formulario manual).
