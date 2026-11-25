package com.suici.roverhood.utils;

import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageButton;

import com.suici.roverhood.R;

// Eye button next to an access code field: shows / hides what was typed (login, create account)
public class PasswordToggle {

    public static void bind(EditText field, ImageButton toggle) {
        if (field == null || toggle == null) return;
        toggle.setOnClickListener(v -> {
            boolean hidden = (field.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
            int selection = field.getSelectionEnd();
            field.setInputType(InputType.TYPE_CLASS_TEXT | (hidden
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            toggle.setImageResource(hidden ? R.drawable.icon_visibility_off : R.drawable.icon_visibility);
            toggle.setContentDescription(hidden ? "Hide access code" : "Show access code");
            field.setSelection(Math.max(0, Math.min(selection, field.length())));
        });
    }
}
