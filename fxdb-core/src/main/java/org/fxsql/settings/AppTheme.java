package org.fxsql.settings;

import atlantafx.base.theme.*;
import java.util.function.Supplier;

public enum AppTheme {
    PRIMER_LIGHT("Primer Light", PrimerLight::new),
    PRIMER_DARK("Primer Dark", PrimerDark::new),
    NORD_LIGHT("Nord Light", NordLight::new),
    NORD_DARK("Nord Dark", NordDark::new),
    CUPERTINO_LIGHT("Cupertino Light", CupertinoLight::new),
    CUPERTINO_DARK("Cupertino Dark", CupertinoDark::new),
    DRACULA("Dracula", Dracula::new);

    private final String displayName;
    private final Supplier<atlantafx.base.theme.Theme> themeSupplier;

    AppTheme(String displayName, Supplier<atlantafx.base.theme.Theme> themeSupplier) {
        this.displayName = displayName;
        this.themeSupplier = themeSupplier;
    }

    public String getDisplayName() { return displayName; }
    public atlantafx.base.theme.Theme createTheme() { return themeSupplier.get(); }
}