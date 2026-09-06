package com.ogautam.letters.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.ogautam.letters.ui.theme.LettersPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A date rendered as plain paper text that opens a picker when tapped — the web app used a
 * bare `<input type="date">` styled to disappear into the letter, and a boxed Material field
 * would read as a form control on a sheet of paper.
 *
 * The picker speaks UTC epoch millis; every conversion here pins to UTC so a device east or
 * west of it cannot land the selection on the neighbouring day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
) {
    var open by remember { mutableStateOf(false) }

    Text(
        text = formatShort(date),
        style = textStyle,
        modifier = modifier.clickable { open = true },
    )

    if (open) {
        val minMillis = minDate?.toUtcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    minMillis == null || utcTimeMillis >= minMillis

                override fun isSelectableYear(year: Int) =
                    minDate == null || year >= minDate.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateChange(it.toUtcLocalDate()) }
                    open = false
                }) { Text("OK", color = LettersPalette.Brown) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text("Cancel", color = LettersPalette.Muted)
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
