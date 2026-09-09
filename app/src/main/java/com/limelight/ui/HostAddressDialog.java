package com.limelight.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.KeyEvent;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.List;

public final class HostAddressDialog {
    public interface Listener {
        void onSelected(ComputerDetails.AddressTuple address);
    }

    private HostAddressDialog() {}

    public static AlertDialog show(Activity activity, ComputerDetails computer, Listener listener) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_host_address, null);
        TextView summary = content.findViewById(R.id.hostAddressSummary);
        summary.setText(activity.getString(R.string.pc_address_summary, computer.name,
                computer.activeAddress == null ? activity.getString(R.string.pc_actions_unavailable)
                        : computer.activeAddress.toString()));
        List<ComputerDetails.AddressTuple> addresses = computer.getSelectableAddresses();
        List<String> labels = new ArrayList<>();
        labels.add(activity.getString(R.string.pc_address_automatic));
        int checked = 0;
        for (ComputerDetails.AddressTuple address : addresses) {
            labels.add(address.toString());
            if (computer.preferredAddress != null
                    && address.port == computer.preferredAddress.port
                    && address.address.equalsIgnoreCase(computer.preferredAddress.address)) {
                checked = labels.size() - 1;
            }
        }
        ListView list = content.findViewById(R.id.hostAddressList);
        list.getLayoutParams().height = Math.min(UiHelper.dpToPx(activity, labels.size() * 60),
                activity.getResources().getDisplayMetrics().heightPixels / 3);
        list.setAdapter(new ArrayAdapter<>(activity, R.layout.item_host_address, labels));
        list.setItemChecked(checked, true);
        list.setSelection(checked);
        View close = content.findViewById(R.id.hostAddressClose);
        AlertDialog dialog = AppDialog.createCustomDialog(activity, content, true);
        if (dialog == null) {
            return null;
        }
        close.setOnClickListener(v -> dialog.dismiss());
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            listener.onSelected(position == 0 ? null : addresses.get(position - 1));
        });
        list.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BUTTON_A) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                int position = list.getSelectedItemPosition();
                if (position == ListView.INVALID_POSITION) {
                    position = list.getCheckedItemPosition();
                }
                if (position != ListView.INVALID_POSITION) {
                    list.performItemClick(list.getSelectedView(), position, position);
                }
            }
            return true;
        });
        AppDialog.showCustomDialog(activity, dialog, 0.85f, 440, list, close, close);
        return dialog;
    }
}
