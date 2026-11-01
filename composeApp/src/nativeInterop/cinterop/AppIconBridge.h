#pragma once

#include <stdbool.h>

typedef void (*KAPlayAppIconCompletion)(bool);

bool KAPlaySupportsAlternateAppIcons(void);
bool KAPlayIsCurrentAlternateAppIcon(const char *name);
void KAPlaySetAlternateAppIconName(const char *name, KAPlayAppIconCompletion completion);
