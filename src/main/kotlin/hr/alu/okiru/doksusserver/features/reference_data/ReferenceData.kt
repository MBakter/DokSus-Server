package hr.alu.okiru.doksusserver.features.reference_data

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

enum class AnalysisType(
    val abbr: String,
    val nameEn: String,
    val nameHr: String
) {
    VIS("VIS", "Visible Light Photography", "Fotografija u vidljivom svjetlu"),
    GL("GL", "Grazing Light Photography", "todo"),
    UVF("UVF", "Ultra Violet Fluorescence", "todo"),
    IRP(
        "IRP",
        "InfraRed Photography / InfraRed Reflectography / InfraRed False Color Photography",
        "todo"
    ),
    XRR("XRR", "X-Ray Radiography", "todo"),
    XRF("XRF", "X-Ray Fluorescence", "todo"),
    RS("RS", "Raman Spectroscopy", "todo"),
    FTIR("FTIR", "Fourier Transform InfraRed Spectroscopy", "todo"),
    FORS("FORS", "Fiber Optics Reflectance Spectroscopy", "todo"),
    SEM(
        "SEM",
        "Scanning Electron Microscopy / Scanning Electron Microscopy–Energy Dispersive X-ray Spectroscopy",
        "todo"
    ),
    OM("OM", "Optical Microscopy", "todo"),
    PLM("PLM", "Polarized Light Microscopy", "todo"),
    PIXE("PIXE", "Particle (Proton) Induced X-ray Emission", "todo"),
    PIGE("PIGE", "Particle (Proton) Induced Gamma-ray Emission", "todo"),
    RBS("RBS", "Rutherford Backscattering", "todo"),
    ST("-", "Synchrotron-based Techniques", "todo"),
    AAS("AAS", "Atomic Absorption Spectroscopy", "todo"),
    AES("AES", "Atomic Emission Spectroscopy", "todo"),
    UVVIS("UVVIS", "UltraViolet-Visible Light Spectroscopy", "todo"),
    TLC("TLC", "Thin-Layer Chromatography", "todo"),
    GCMS("GCMS", "Gas Chromatography–Mass Spectrometry", "todo"),
    TL("TL", "Thermoluminescence", "todo"),
    OL("OL", "Optical Luminescence", "todo"),
    C14("C14", "Radiocarbon Dating", "todo"),
    XRD("XRD", "X-Ray Diffraction", "todo"),
    LIBS("LIBS", "Laser-Induced Breakdown Spectroscopy", "todo"),
    TEM("TEM", "Transmission Electron Microscopy", "todo"),
    AFM("AFM", "Atomic Force Microscopy", "todo"),
    HM("-", "Hygroscopic Measurements", "todo"),
    G("-", "Granulometry", "todo"),
    BFR("-", "Binder/Filler Ratio", "todo"),
    DRMS("DRMS", "Drilling Resistance Measurement System", "todo"),
    S3D("-", "3D Scanning", "todo"),
    AS("-", "Analysis of Salts", "todo")
}