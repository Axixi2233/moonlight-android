package com.limelight.ui.home;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

import java.util.ArrayList;
import java.util.List;

final class HomeHostAdapter extends RecyclerView.Adapter<HomeHostAdapter.HostViewHolder> {
    static final String ADD_HOST_KEY = "__moonlight_add_host__";

    // Mirrors the per-card accent pairs used by the iOS home carousel.
    private static final int[] CARD_ACCENTS = {
            0xFF8252FF,
            0xFF685BEE,
            0xFFBE4CFF,
            0xFF27B4D5,
            0xFFF25EAA,
            0xFF31C2B0
    };
    private static final int[] CARD_SECONDARY_ACCENTS = {
            0xFF596AFF,
            0xFFA56CFF,
            0xFFF065AE,
            0xFF735CFF,
            0xFF8A58FF,
            0xFF557CFF
    };
    private static final int ADD_CARD_ACCENT = 0xFFA78BFA;
    private static final int ADD_CARD_SECONDARY_ACCENT = 0xFF7C3AED;
    private static final int UNKNOWN_CARD_ACCENT = 0xFF737B8E;
    private static final int UNKNOWN_CARD_SECONDARY_ACCENT = 0xFF4A5264;
    private static final int OFFLINE_CARD_ACCENT = 0xFF73757E;
    private static final int OFFLINE_CARD_SECONDARY_ACCENT = 0xFF454750;
    private static final int COLOR_SURFACE_START = 0xFF191732;
    private static final int COLOR_SURFACE_END = 0xFF090B1A;
    private static final int UNKNOWN_SURFACE_START = 0xFF1C2029;
    private static final int UNKNOWN_SURFACE_END = 0xFF0D1016;
    private static final int OFFLINE_SURFACE_START = 0xFF202126;
    private static final int OFFLINE_SURFACE_END = 0xFF0F1013;
    private static final int ADD_SURFACE_START = 0xFF24183A;
    private static final int ADD_SURFACE_END = 0xFF110C20;

    interface InteractionListener {
        void onCardClicked(int position, View sourceView);
        void onCardFocused(int position, View sourceView);
        void onCardFocusLost(int position, View sourceView);
        void onCardLongPressed(int position, View sourceView);
        void onMoreActions(int position, View sourceView);
    }

    private final List<PcView.ComputerObject> computers = new ArrayList<>();
    private final InteractionListener interactionListener;
    private int selectedPosition;
    private int cardWidth;

    HomeHostAdapter(InteractionListener interactionListener) {
        this.interactionListener = interactionListener;
        setHasStableIds(true);
    }

    void setComputers(List<PcView.ComputerObject> newComputers) {
        computers.clear();
        computers.addAll(newComputers);
        selectedPosition = Math.min(selectedPosition, getItemCount() - 1);
        notifyDataSetChanged();
    }

    void setCardWidth(int cardWidth) {
        if (this.cardWidth != cardWidth) {
            this.cardWidth = cardWidth;
            notifyDataSetChanged();
        }
    }

    int getHostCount() {
        return computers.size();
    }

    boolean isAddPosition(int position) {
        return position == computers.size();
    }

    PcView.ComputerObject getComputerAt(int position) {
        if (position < 0 || position >= computers.size()) {
            return null;
        }
        return computers.get(position);
    }

    String getSelectionKey(int position) {
        PcView.ComputerObject computer = getComputerAt(position);
        if (computer == null || computer.details == null || computer.details.uuid == null) {
            return ADD_HOST_KEY;
        }
        return computer.details.uuid;
    }

    int findPositionForKey(String key) {
        if (key == null) {
            return computers.isEmpty() ? 0 : 0;
        }
        if (ADD_HOST_KEY.equals(key)) {
            return computers.size();
        }
        for (int index = 0; index < computers.size(); index++) {
            ComputerDetails details = computers.get(index).details;
            if (details != null && key.equals(details.uuid)) {
                return index;
            }
        }
        return computers.isEmpty() ? 0 : Math.min(selectedPosition, computers.size() - 1);
    }

    void setSelectedPosition(int position) {
        int boundedPosition = Math.max(0, Math.min(position, getItemCount() - 1));
        if (selectedPosition == boundedPosition) {
            return;
        }
        int oldPosition = selectedPosition;
        selectedPosition = boundedPosition;
        notifyItemChanged(oldPosition);
        notifyItemChanged(selectedPosition);
    }

    int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    public long getItemId(int position) {
        if (isAddPosition(position)) {
            return Long.MIN_VALUE;
        }
        ComputerDetails details = computers.get(position).details;
        return details != null && details.uuid != null
                ? details.uuid.hashCode()
                : position;
    }

    @Override
    public int getItemCount() {
        return computers.size() + 1;
    }

    @NonNull
    @Override
    public HostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_host_card, parent, false);
        RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(
                cardWidth > 0 ? cardWidth : dp(parent, 300),
                ViewGroup.LayoutParams.MATCH_PARENT);
        view.setLayoutParams(layoutParams);
        return new HostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HostViewHolder holder, int position) {
        RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
        layoutParams.width = cardWidth > 0 ? cardWidth : dp(holder.itemView, 300);
        holder.itemView.setLayoutParams(layoutParams);
        holder.itemView.setSelected(position == selectedPosition);
        applyCardTheme(holder, position);

        if (isAddPosition(position)) {
            bindAddCard(holder);
        }
        else {
            bindHostCard(holder, computers.get(position), position);
        }

        holder.itemView.setOnClickListener(view -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                interactionListener.onCardClicked(adapterPosition, view);
            }
        });
        holder.itemView.setOnLongClickListener(view -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || isAddPosition(adapterPosition)) {
                return false;
            }
            interactionListener.onCardLongPressed(adapterPosition, view);
            return true;
        });
        holder.itemView.setOnFocusChangeListener((view, hasFocus) -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                if (hasFocus) {
                    interactionListener.onCardFocused(adapterPosition, view);
                }
                else {
                    interactionListener.onCardFocusLost(adapterPosition, view);
                }
            }
        });
        holder.moreButton.setOnClickListener(view -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION && !isAddPosition(adapterPosition)) {
                interactionListener.onMoreActions(adapterPosition, holder.itemView);
            }
        });
    }

    private void bindAddCard(HostViewHolder holder) {
        holder.badge.setText(R.string.home_add_host);
        holder.moreButton.setVisibility(View.GONE);
        holder.icon.setImageResource(R.drawable.icon_app_add_circle);
        holder.title.setText(R.string.home_add_host_title);
        holder.address.setText(R.string.home_add_host_subtitle);
        holder.meta.setText(R.string.home_discovery_active);
        holder.status.setText(R.string.home_always_available);
        holder.primaryAction.setText(R.string.home_start_add);
        setDotColor(holder.statusDot, color(holder.itemView, R.color.home_accent_bright));
        holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                R.string.home_host_card_accessibility,
                holder.itemView.getContext().getString(R.string.home_add_host_title),
                holder.itemView.getContext().getString(R.string.home_discovery_active),
                holder.itemView.getContext().getString(R.string.home_start_add)));
    }

    private void bindHostCard(HostViewHolder holder, PcView.ComputerObject computer, int position) {
        ComputerDetails details = computer.details;
        boolean paired = details.pairState == PairingManager.PairState.PAIRED;
        boolean unpaired = details.pairState == PairingManager.PairState.NOT_PAIRED;

        holder.badge.setText(details.nvidiaServer
                ? R.string.home_host_source_gamestream
                : R.string.home_host_source_sunshine);
        holder.moreButton.setVisibility(View.VISIBLE);
        holder.moreButton.setContentDescription(holder.itemView.getContext().getString(
                R.string.home_host_more, details.name));
        holder.icon.setImageResource(R.drawable.ic_computer);
        holder.title.setText(details.name);

        AddressDisplay addressDisplay = getAddressDisplay(details, holder.itemView.getContext());
        holder.address.setText(addressDisplay.address);
        int pairStateRes;
        if (details.state == ComputerDetails.State.UNKNOWN || (!paired && !unpaired)) {
            pairStateRes = R.string.pc_actions_pair_unknown;
        }
        else {
            pairStateRes = paired
                    ? R.string.home_host_pair_paired
                    : R.string.home_host_pair_unpaired;
        }
        holder.meta.setText(holder.itemView.getContext().getString(addressDisplay.labelRes)
                + " · " + holder.itemView.getContext().getString(pairStateRes));

        int statusRes;
        int actionRes;
        int statusColor;
        if (details.state == ComputerDetails.State.UNKNOWN) {
            statusRes = R.string.home_host_checking;
            actionRes = R.string.home_host_actions;
            statusColor = color(holder.itemView, R.color.home_accent_bright);
        }
        else if (details.state == ComputerDetails.State.OFFLINE) {
            statusRes = R.string.home_host_offline;
            actionRes = R.string.home_host_actions;
            statusColor = color(holder.itemView, R.color.home_secondary_text);
        }
        else if (unpaired) {
            statusRes = R.string.home_host_pair_required;
            actionRes = R.string.home_host_pair;
            statusColor = color(holder.itemView, R.color.home_warning);
        }
        else if (!paired) {
            statusRes = R.string.home_host_checking;
            actionRes = R.string.home_host_actions;
            statusColor = color(holder.itemView, R.color.home_accent_bright);
        }
        else {
            statusRes = details.runningGameId != 0
                    ? R.string.home_host_running
                    : R.string.home_host_ready;
            actionRes = R.string.home_host_view_apps;
            statusColor = color(holder.itemView, R.color.home_connected);
        }

        holder.status.setText(statusRes);
        holder.primaryAction.setText(actionRes);
        setDotColor(holder.statusDot, statusColor);
        holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                R.string.home_host_card_accessibility,
                details.name,
                holder.itemView.getContext().getString(statusRes),
                holder.itemView.getContext().getString(actionRes)));
    }

    private void applyCardTheme(HostViewHolder holder, int position) {
        int accent;
        int secondaryAccent;
        int surfaceStart;
        int surfaceEnd;
        if (isAddPosition(position)) {
            accent = ADD_CARD_ACCENT;
            secondaryAccent = ADD_CARD_SECONDARY_ACCENT;
            surfaceStart = ADD_SURFACE_START;
            surfaceEnd = ADD_SURFACE_END;
        }
        else {
            ComputerDetails details = computers.get(position).details;
            if (details != null && details.state == ComputerDetails.State.ONLINE) {
                String stableKey = details.uuid == null ? details.name : details.uuid;
                int paletteIndex = stableKey == null
                        ? 0 : Math.floorMod(stableKey.hashCode(), CARD_ACCENTS.length);
                accent = CARD_ACCENTS[paletteIndex];
                secondaryAccent = CARD_SECONDARY_ACCENTS[paletteIndex];
                surfaceStart = COLOR_SURFACE_START;
                surfaceEnd = COLOR_SURFACE_END;
            }
            else if (details != null && details.state == ComputerDetails.State.OFFLINE) {
                accent = OFFLINE_CARD_ACCENT;
                secondaryAccent = OFFLINE_CARD_SECONDARY_ACCENT;
                surfaceStart = OFFLINE_SURFACE_START;
                surfaceEnd = OFFLINE_SURFACE_END;
            }
            else {
                accent = UNKNOWN_CARD_ACCENT;
                secondaryAccent = UNKNOWN_CARD_SECONDARY_ACCENT;
                surfaceStart = UNKNOWN_SURFACE_START;
                surfaceEnd = UNKNOWN_SURFACE_END;
            }
        }
        holder.itemView.setBackground(createCardBackground(
                holder.itemView, accent, secondaryAccent, surfaceStart, surfaceEnd));
        holder.iconTile.setBackground(createIconBackground(
                holder.iconTile, accent, surfaceStart, surfaceEnd));
        holder.primaryAction.setBackground(createActionBackground(
                holder.primaryAction,
                accent,
                secondaryAccent));
    }

    private static StateListDrawable createCardBackground(View view, int accent,
                                                           int secondaryAccent,
                                                           int surfaceStart,
                                                           int surfaceEnd) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[] {android.R.attr.state_focused},
                createCardLayer(view, Color.WHITE, Color.WHITE, accent, secondaryAccent,
                        surfaceStart, surfaceEnd, 3));
        states.addState(
                new int[] {android.R.attr.state_selected},
                createCardLayer(
                        view,
                        withAlpha(accent, 0xD0),
                        withAlpha(secondaryAccent, 0xB0),
                        accent,
                        secondaryAccent,
                        surfaceStart,
                        surfaceEnd,
                        2));
        states.addState(
                new int[] {},
                createCardLayer(
                        view,
                        withAlpha(accent, 0x50),
                        withAlpha(secondaryAccent, 0x38),
                        accent,
                        secondaryAccent,
                        surfaceStart,
                        surfaceEnd,
                        1));
        return states;
    }

    private static Drawable createCardLayer(View view, int borderStart, int borderEnd,
                                             int accent, int secondaryAccent,
                                             int surfaceStart, int surfaceEnd, int insetDp) {
        GradientDrawable border = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {borderStart, borderEnd});
        border.setCornerRadius(dp(view, 28));

        GradientDrawable surface = new GradientDrawable(
                 GradientDrawable.Orientation.TL_BR,
                 new int[] {
                         blendColor(surfaceStart, accent, 0.22f),
                         blendColor(surfaceEnd, secondaryAccent, 0.10f)
                 });
        surface.setCornerRadius(dp(view, 28 - insetDp));

        LayerDrawable layers = new LayerDrawable(new Drawable[] {border, surface});
        int inset = dp(view, insetDp);
        layers.setLayerInset(1, inset, inset, inset, inset);
        return layers;
    }

    private static Drawable createActionBackground(View view, int accent, int secondaryAccent) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {accent, secondaryAccent});
        drawable.setCornerRadius(dp(view, 100));
        return drawable;
    }

    private static Drawable createIconBackground(View view, int accent,
                                                 int surfaceStart, int surfaceEnd) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                        blendColor(surfaceStart, accent, 0.38f),
                        blendColor(surfaceEnd, accent, 0.20f)
                });
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(dp(view, 1), withAlpha(accent, 0x68));
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int blendColor(int base, int overlay, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(base) * inverse + Color.red(overlay) * amount),
                Math.round(Color.green(base) * inverse + Color.green(overlay) * amount),
                Math.round(Color.blue(base) * inverse + Color.blue(overlay) * amount));
    }

    private AddressDisplay getAddressDisplay(ComputerDetails details, android.content.Context context) {
        if (details.activeAddress != null) {
            return new AddressDisplay(details.activeAddress.address, addressLabel(details, details.activeAddress));
        }
        if (details.localAddress != null) {
            return new AddressDisplay(details.localAddress.address, R.string.home_host_connection_local);
        }
        if (details.ipv6Address != null) {
            return new AddressDisplay(details.ipv6Address.address, R.string.home_host_connection_ipv6);
        }
        if (details.manualAddress != null) {
            return new AddressDisplay(details.manualAddress.address, R.string.home_host_connection_manual);
        }
        if (details.remoteAddress != null) {
            return new AddressDisplay(details.remoteAddress.address, R.string.home_host_connection_remote);
        }
        return new AddressDisplay(
                context.getString(R.string.home_host_connection_unknown),
                R.string.home_host_connection_unknown);
    }

    private int addressLabel(ComputerDetails details, ComputerDetails.AddressTuple activeAddress) {
        if (activeAddress.equals(details.localAddress)) {
            return R.string.home_host_connection_local;
        }
        if (activeAddress.equals(details.ipv6Address)) {
            return R.string.home_host_connection_ipv6;
        }
        if (activeAddress.equals(details.manualAddress)) {
            return R.string.home_host_connection_manual;
        }
        if (activeAddress.equals(details.remoteAddress)) {
            return R.string.home_host_connection_remote;
        }
        return R.string.home_host_connection_unknown;
    }

    private static void setDotColor(View view, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        view.setBackground(drawable);
    }

    private static int color(View view, int colorRes) {
        return view.getResources().getColor(colorRes);
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static final class AddressDisplay {
        final String address;
        final int labelRes;

        AddressDisplay(String address, int labelRes) {
            this.address = address;
            this.labelRes = labelRes;
        }
    }

    static final class HostViewHolder extends RecyclerView.ViewHolder {
        final TextView badge;
        final ImageButton moreButton;
        final View iconTile;
        final ImageView icon;
        final TextView title;
        final TextView address;
        final TextView meta;
        final View statusDot;
        final TextView status;
        final TextView primaryAction;

        HostViewHolder(View itemView) {
            super(itemView);
            badge = itemView.findViewById(R.id.homeHostBadge);
            moreButton = itemView.findViewById(R.id.homeHostMore);
            iconTile = itemView.findViewById(R.id.homeHostIconTile);
            icon = itemView.findViewById(R.id.homeHostIcon);
            title = itemView.findViewById(R.id.homeHostTitle);
            address = itemView.findViewById(R.id.homeHostAddress);
            meta = itemView.findViewById(R.id.homeHostMeta);
            statusDot = itemView.findViewById(R.id.homeHostStatusDot);
            status = itemView.findViewById(R.id.homeHostStatus);
            primaryAction = itemView.findViewById(R.id.homeHostPrimaryAction);
        }
    }
}
