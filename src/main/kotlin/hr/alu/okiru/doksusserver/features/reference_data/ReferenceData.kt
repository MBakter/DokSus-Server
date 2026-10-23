package hr.alu.okiru.doksusserver.features.reference_data

/**
 * Fixed list of document categories. The enum name is stored in the database, [displayName] is shown
 * in the UI. Changing or removing an existing name requires a database migration.
 */
enum class Category(val displayName: String) {
    UNSPECIFIED("Unspecified"),
    DRVENI_PREDMETI("Drveni predmeti"),
    SLIKE_NA_PLATNU("Slike na platnu"),
    ZIDNE_SLIKE("Zidne slike"),
    KAMENA_I_ARHITEKTONSKA_PLASTIKA("Kamena i arhitektonska plastika"),
    OSTALI_MATERIJALI("Ostali materijali"),
    ISTRAZIVACKI_RADOVI_I_REFERENTNI_MATERIJALI("Istraživački radovi i referentni materijali"),
    DIPLOMSKI_I_SEMINARSKI_RADOVI("Diplomski i seminarski radovi")
}

/** Fixed list of scientific analysis methods selectable on a document, with English and Croatian names. */
enum class AnalysisType(
    val abbr: String,
    val nameEn: String,
    val nameHr: String
) {
    VIS("VIS", "Visible Light Photography", "Fotografija u vidljivom svjetlu"),
    GL("GL", "Grazing Light Photography", "Fotografija u koso upadnom (kliznom) svjetlu"),
    UVF("UVF", "Ultra Violet Fluorescence", "Ultraljubičasta fluorescencija"),
    IRP(
        "IRP/IRR/IRFC",
        "InfraRed Photography / InfraRed Reflectography / InfraRed False Color Photography",
        "Infracrvena fotografija / infracrvena reflektografija / infracrvena fotografija lažnih boja"
    ),
    XRR("XRR", "X-Ray Radiography", "Rendgenska radiografija"),
    XRF("XRF", "X-Ray Fluorescence", "Rendgenska fluorescencija"),
    RS("RS", "Raman Spectroscopy", "Ramanova spektroskopija"),
    FTIR("FT-IR", "Fourier Transform InfraRed Spectroscopy", "Infracrvena spektroskopija s Fourierovom transformacijom"),
    FORS("FORS", "Fiber Optics Reflectance Spectroscopy", "Refleksijska spektroskopija optičkim vlaknom"),
    SEM(
        "SEM/SEM-EDX",
        "Scanning Electron Microscopy / Scanning Electron Microscopy–Energy Dispersive X-ray Spectroscopy",
        "Pretražna elektronska mikroskopija / pretražna elektronska mikroskopija s energijski disperzivnom rendgenskom spektroskopijom"
    ),
    OM("OM", "Optical Microscopy", "Optička mikroskopija"),
    PLM("PLM", "Polarized Light Microscopy", "Mikroskopija u polariziranom svjetlu"),
    PIXE("PIXE", "Particle (Proton) Induced X-ray Emission", "Rendgenska emisija inducirana česticama (protonima)"),
    PIGE("PIGE", "Particle (Proton) Induced Gamma-ray Emission", "Gama-emisija inducirana česticama (protonima)"),
    RBS("RBS", "Rutherford Backscattering", "Rutherfordovo povratno raspršenje"),
    ST("-", "Synchrotron-based Techniques", "Tehnike temeljene na sinkrotronskom zračenju"),
    AAS("AAS", "Atomic Absorption Spectroscopy", "Atomska apsorpcijska spektroskopija"),
    AES("AES", "Atomic Emission Spectroscopy", "Atomska emisijska spektroskopija"),
    UVVIS("UV-Vis", "UltraViolet-Visible Light Spectroscopy", "Ultraljubičasto–vidljiva spektroskopija"),
    TLC("TLC", "Thin-Layer Chromatography", "Tankoslojna kromatografija"),
    GCMS("GC-MS", "Gas Chromatography–Mass Spectrometry", "Plinska kromatografija–masena spektrometrija"),
    TL("TL", "Thermoluminescence", "Termoluminiscencija"),
    OL("OL", "Optical Luminescence", "Optička luminiscencija"),
    C14("C-14", "Radiocarbon Dating", "Radiokarbonsko datiranje"),
    XRD("XRD", "X-Ray Diffraction", "Rendgenska difrakcija"),
    LIBS("LIBS", "Laser-Induced Breakdown Spectroscopy", "Spektroskopija laserski inducirane plazme"),
    TEM("TEM", "Transmission Electron Microscopy", "Transmisijska elektronska mikroskopija"),
    AFM("AFM", "Atomic Force Microscopy", "Mikroskopija atomskih sila"),
    HM("-", "Hygroscopic Measurements", "Higroskopna mjerenja"),
    G("-", "Granulometry", "Granulometrijska analiza"),
    BFR("-", "Binder/Filler Ratio", "Omjer veziva i punila"),
    DRMS("DRMS", "Drilling Resistance Measurement System", "Sustav za mjerenje otpora pri bušenju"),
    S3D("-", "3D Scanning", "3D skeniranje"),
    AS("-", "Analysis of Salts", "Analiza soli")
}