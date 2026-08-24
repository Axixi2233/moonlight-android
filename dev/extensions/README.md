# Extension development registry

This registry identifies project-local extensions and their upstream integration points.
Use the following stable marker format when preparing or reviewing a pull request:

```text
EXTENSION DEVELOPMENT [<extension-id>] [ADDED|MODIFIED]
```

- `ADDED` marks a file created and owned by an extension.
- `MODIFIED` marks an integration point in an existing upstream file. Modified blocks use
  matching `BEGIN` and `END` markers.
- Extension IDs remain stable when code is moved or split.

## EXT-IME-ACCESSORY-BAR

- Change type: extension development
- Status: experimental
- Added: 2026-08-24
- Purpose: display two rows of desktop shortcut keys directly above a docked Android system IME.
- Added files:
  - `app/src/main/java/com/limelight/extensions/keyboard/ImeKeyboardExtensionController.java`
  - `app/src/main/res/layout/extension_ime_keyboard_bar.xml`
  - `app/src/main/res/drawable/extension_ime_keyboard_key.xml`
  - `app/src/main/res/values/extension_ime_keyboard_styles.xml`
- Modified files:
  - `app/src/main/java/com/limelight/Game.java`
- Upstream isolation: the controller owns its view, resources, IME observation, and key state;
  `Game` only attaches it and forwards key events through the existing keyboard pipeline.

## EXT-IME-AT-SIGN

- Change type: compatibility extension development
- Status: experimental
- Added: 2026-08-24
- Purpose: avoid Sunshine/Linux `Ctrl+Shift+U` Unicode composition for a single-character
  Android IME commit of `@` by sending normalized `Shift+2` key packets instead.
- Added files:
  - `app/src/main/java/com/limelight/extensions/keyboard/ImeTextInputCompatibilityExtension.java`
  - `app/src/test/java/com/limelight/extensions/keyboard/ImeTextInputCompatibilityExtensionTest.java`
- Modified files:
  - `app/src/main/java/com/limelight/Game.java`
- Compatibility constraint: `Shift+2` assumes a US/QWERTY host keyboard layout. Other text and
  multi-character commits remain on the original UTF-8 path.
- Related upstream issue: `LizardByte/Sunshine#5274`.

## Review lookup

Locate all extension-owned files and upstream modifications with:

```bash
rg -n "EXTENSION DEVELOPMENT \[EXT-IME-(ACCESSORY-BAR|AT-SIGN)\]" app/src dev/extensions
```
