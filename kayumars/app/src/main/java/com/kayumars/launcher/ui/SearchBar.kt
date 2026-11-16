package com.kayumars.launcher.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

/**
 * High-performance, zero-garbage text listener attached to the search input.
 */
class SearchBar(
    private val editText: EditText,
    private val onQueryChange: (String) -> Unit
) {
    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onQueryChange(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    init {
        editText.addTextChangedListener(watcher)
    }

    fun clear() {
        editText.setText("")
    }

    fun getText(): String = editText.text?.toString().orEmpty()
}
