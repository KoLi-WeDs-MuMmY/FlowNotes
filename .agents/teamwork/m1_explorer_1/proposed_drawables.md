# Recommended Drawables for FlowNotes

The following drawable XML resources should be created by the Worker under `app/src/main/res/drawable/`:

### 1. `app/src/main/res/drawable/bg_folder_chip.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#F1F5F9" />
    <corners android:radius="18dp" />
    <stroke
        android:width="1dp"
        android:color="#E2E8F0" />
</shape>
```

### 2. `app/src/main/res/drawable/bg_tool_button.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#F8FAFC" />
    <stroke
        android:width="1dp"
        android:color="#CBD5E1" />
</shape>
```

### 3. `app/src/main/res/drawable/bg_card_cover.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#3B82F6" />
    <corners
        android:topLeftRadius="16dp"
        android:topRightRadius="16dp"
        android:bottomLeftRadius="0dp"
        android:bottomRightRadius="0dp" />
</shape>
```
