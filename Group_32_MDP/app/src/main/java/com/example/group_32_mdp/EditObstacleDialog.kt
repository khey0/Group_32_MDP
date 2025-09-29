package com.example.group_32_mdp

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.fragment.app.DialogFragment

class EditObstacleDialog : DialogFragment() {

    private var obstacle: Obstacle? = null
    private var listener: OnObstacleUpdatedListener? = null
    private var onDismissListener: (() -> Unit)? = null

    interface OnObstacleUpdatedListener {
        fun onObstacleUpdated(obstacle: Obstacle)
    }
    
    fun setOnDismissListener(listener: () -> Unit) {
        onDismissListener = listener
    }

    companion object {
        fun newInstance(obstacle: Obstacle): EditObstacleDialog {
            val dialog = EditObstacleDialog()
            dialog.obstacle = obstacle
            return dialog
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnObstacleUpdatedListener) {
            listener = context
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_edit_obstacle, null)

        val directionSpinner = view.findViewById<Spinner>(R.id.directionSpinner)
        val xCoordinateEdit = view.findViewById<EditText>(R.id.xCoordinateEdit)
        val yCoordinateEdit = view.findViewById<EditText>(R.id.yCoordinateEdit)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        val saveButton = view.findViewById<Button>(R.id.saveButton)

        // Set up direction spinner
        val directions = arrayOf("NORTH", "SOUTH", "EAST", "WEST")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, directions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        directionSpinner.adapter = adapter

        // Set current values (Y is now bottom-up: 0 at bottom, 19 at top)
        obstacle?.let { obs ->
            xCoordinateEdit.setText(obs.x.toString())
            yCoordinateEdit.setText(obs.y.toString())
            
            val directionIndex = directions.indexOf(obs.direction.name)
            if (directionIndex >= 0) {
                directionSpinner.setSelection(directionIndex)
            }
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        saveButton.setOnClickListener {
            val newX = xCoordinateEdit.text.toString().toIntOrNull() ?: obstacle?.x ?: 0
            val newY = yCoordinateEdit.text.toString().toIntOrNull() ?: obstacle?.y ?: 0
            val newDirection = Direction.valueOf(directions[directionSpinner.selectedItemPosition])

            obstacle?.let { obs ->
                // Single upsert that moves if needed, or updates direction in place
                GridData.upsertObstacleById(obs.id, newX, newY, newDirection)
                val updated = obs.copy(x = newX, y = newY, direction = newDirection)
                listener?.onObstacleUpdated(updated)
            }
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }
    
    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }
}
