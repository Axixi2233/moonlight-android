package com.limelight.ui;

import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

public final class PcActionsDialog extends BaseGameMenuDialog {
    public interface Listener {
        void onResumeStream(ComputerDetails computer);
        void onQuitStream(ComputerDetails computer);
        void onOpenAppList(ComputerDetails computer);
        void onPairComputer(ComputerDetails computer);
        void onWakeComputer(ComputerDetails computer);
        void onShowGameStreamEol();
        void onTestNetwork();
        void onDeleteComputer(ComputerDetails computer);
        void onDismiss();
    }

    private ComputerDetails computer;
    private Listener listener;

    public void setComputer(ComputerDetails computer) {
        this.computer = computer;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.dialog_context_actions;
    }

    @Override
    public void bindView(View view) {
        if (computer == null) {
            dismiss();
            return;
        }

        ImageButton backButton = view.findViewById(R.id.ibtn_pc_actions_back);
        TextView titleView = view.findViewById(R.id.tv_pc_actions_title);
        TextView detailsView = view.findViewById(R.id.tv_pc_actions_details);
        Button resumeButton = view.findViewById(R.id.btn_pc_actions_resume);
        Button quitButton = view.findViewById(R.id.btn_pc_actions_quit);
        Button appListButton = view.findViewById(R.id.btn_pc_actions_app_list);
        Button pairButton = view.findViewById(R.id.btn_pc_actions_pair);
        Button wakeButton = view.findViewById(R.id.btn_pc_actions_wake);
        Button gameStreamEolButton = view.findViewById(R.id.btn_pc_actions_gamestream_eol);
        Button networkTestButton = view.findViewById(R.id.btn_pc_actions_network_test);
        Button deleteButton = view.findViewById(R.id.btn_pc_actions_delete);

        titleView.setText(valueOrUnavailable(computer.name));
        detailsView.setText(getString(
                R.string.pc_actions_details_format,
                getStateText(computer.state),
                addressText(computer.activeAddress),
                valueOrUnavailable(computer.uuid),
                addressText(computer.localAddress),
                addressText(computer.remoteAddress),
                addressText(computer.ipv6Address),
                addressText(computer.manualAddress),
                valueOrUnavailable(computer.macAddress),
                getPairStateText(computer.pairState),
                computer.runningGameId,
                computer.httpsPort));

        boolean canOpenApps = computer.state == ComputerDetails.State.ONLINE &&
                computer.pairState == PairingManager.PairState.PAIRED;
        boolean canResume = canOpenApps && computer.runningGameId != 0;
        boolean canPair = computer.state == ComputerDetails.State.ONLINE &&
                computer.pairState != PairingManager.PairState.PAIRED;
        boolean canWake = computer.state == ComputerDetails.State.OFFLINE ||
                computer.state == ComputerDetails.State.UNKNOWN;
        boolean showGameStreamEol = canWake || computer.nvidiaServer;
        resumeButton.setVisibility(canResume ? View.VISIBLE : View.GONE);
        quitButton.setVisibility(canResume ? View.VISIBLE : View.GONE);
        pairButton.setVisibility(canPair ? View.VISIBLE : View.GONE);
        wakeButton.setVisibility(canWake ? View.VISIBLE : View.GONE);
        gameStreamEolButton.setVisibility(showGameStreamEol ? View.VISIBLE : View.GONE);
        appListButton.setEnabled(canOpenApps);
        appListButton.setFocusable(canOpenApps);
        appListButton.setAlpha(canOpenApps ? 1f : 0.45f);

        backButton.setOnClickListener(v -> dismiss());
        resumeButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onResumeStream(computer);
            }
        });
        quitButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onQuitStream(computer);
            }
        });
        appListButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onOpenAppList(computer);
            }
        });
        pairButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onPairComputer(computer);
            }
        });
        wakeButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onWakeComputer(computer);
            }
        });
        gameStreamEolButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onShowGameStreamEol();
            }
        });
        networkTestButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onTestNetwork();
            }
        });
        deleteButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onDeleteComputer(computer);
            }
        });

        (canResume ? resumeButton : canPair ? pairButton : canWake ? wakeButton :
                appListButton.isEnabled() ? appListButton : networkTestButton).requestFocus();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (listener != null) {
            listener.onDismiss();
        }
    }

    private String getStateText(ComputerDetails.State state) {
        if (state == ComputerDetails.State.ONLINE) {
            return getString(R.string.pc_actions_state_online);
        }
        if (state == ComputerDetails.State.OFFLINE) {
            return getString(R.string.pc_actions_state_offline);
        }
        return getString(R.string.pc_actions_state_unknown);
    }

    private String getPairStateText(PairingManager.PairState state) {
        if (state == PairingManager.PairState.PAIRED) {
            return getString(R.string.pc_actions_pair_paired);
        }
        if (state == PairingManager.PairState.NOT_PAIRED) {
            return getString(R.string.pc_actions_pair_not_paired);
        }
        return getString(R.string.pc_actions_pair_unknown);
    }

    private String addressText(ComputerDetails.AddressTuple address) {
        return address == null ? getString(R.string.pc_actions_unavailable) : address.toString();
    }

    private String valueOrUnavailable(String value) {
        return value == null || value.length() == 0 ? getString(R.string.pc_actions_unavailable) : value;
    }
}
