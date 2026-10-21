#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Apps that ship in every o1s variant (NG, MTG and AMTG). Inherited from
# device/samsung/o1s/device.mk, outside the WITHOUT_GAPPS / WITH_AMTG gates.

MACK_APPS_PATH := vendor/mack-apps

# Stop when a pinned APK is missing or is not the pinned file (sha256, size,
# signer certificate). Python from prebuilts: host python is not allowed here.
MACK_APPS_CHECK := $(shell prebuilts/build-tools/path/linux-x86/python3 -I \
    $(MACK_APPS_PATH)/tools/check-blobs.py --quiet $(MACK_APPS_PATH) 2>&1)
ifneq ($(MACK_APPS_CHECK),OK)
$(error $(MACK_APPS_PATH): $(MACK_APPS_CHECK) (run $(MACK_APPS_PATH)/fetch-blobs.sh))
endif

PRODUCT_SOONG_NAMESPACES += $(MACK_APPS_PATH)

# Aurora Store (com.aurora.store), GPL-3.0-or-later, see README.md
PRODUCT_PACKAGES += \
    AuroraStore
