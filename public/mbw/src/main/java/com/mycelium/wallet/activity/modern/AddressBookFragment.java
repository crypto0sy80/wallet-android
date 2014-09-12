/*
 * Copyright 2013 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.activity.modern;

import java.util.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.AddressBookManager;
import com.mycelium.wallet.AddressBookManager.Entry;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.ScanRequest;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.ScanActivity;
import com.mycelium.wallet.activity.receive.ReceiveCoinsActivity;
import com.mycelium.wallet.activity.util.EnterAddressLabelUtil;
import com.mycelium.wallet.activity.util.EnterAddressLabelUtil.AddressLabelChangedHandler;
import com.mycelium.wallet.event.AddressBookChanged;
import com.mycelium.wapi.wallet.WalletAccount;
import com.squareup.otto.Subscribe;

public class AddressBookFragment extends Fragment {

   public static final int SCAN_RESULT_CODE = 0;
   public static final String ADDRESS_RESULT_NAME = "address_result";
   public static final String OWN = "own";
   public static final String SELECT_ONLY = "selectOnly";

   private Address mSelectedAddress;
   private MbwManager _mbwManager;
   private Dialog _addDialog;
   private ActionMode currentActionMode;
   private Boolean ownAddresses; // set to null on purpose

   @Override
   public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View ret = Preconditions.checkNotNull(inflater.inflate(R.layout.address_book, container, false));
      ownAddresses = getArguments().getBoolean(OWN);
      boolean isSelectOnly = getArguments().getBoolean(SELECT_ONLY);
      setHasOptionsMenu(!isSelectOnly);
      ListView foreignList = (ListView) ret.findViewById(R.id.lvForeignAddresses);
      if (isSelectOnly) {
         foreignList.setOnItemClickListener(new SelectItemListener());
      } else {
         foreignList.setOnItemClickListener(itemListClickListener);
      }
      return ret;
   }

   private View findViewById(int id) {
      return getView().findViewById(id);
   }

   @Override
   public void onAttach(Activity activity) {
      _mbwManager = MbwManager.getInstance(getActivity().getApplication());
      super.onAttach(activity);
   }

   @Override
   public void onDetach() {
      super.onDetach();
   }

   @Override
   public void onResume() {
      _mbwManager.getEventBus().register(this);
      updateUi();
      super.onResume();
   }

   @Override
   public void onPause() {
      _mbwManager.getEventBus().unregister(this);
      super.onPause();
   }

   @Override
   public void onDestroy() {
      if (_addDialog != null && _addDialog.isShowing()) {
         _addDialog.dismiss();
      }
      super.onDestroy();
   }

   private void updateUi() {
      if (!isAdded()) {
         return;
      }
      if (ownAddresses) {
         updateUiMine();
      } else {
         updateUiForeign();
      }
   }

   private void updateUiMine() {
      Map<Address, String> entries = new HashMap<Address, String>();
      for (WalletAccount account : Utils.sortAccounts(_mbwManager.getWalletManager(false).getActiveAccounts())) {
         String name = _mbwManager.getMetadataStorage().getLabelByAccount(account.getId());
         entries.put(account.getReceivingAddress(), name);
      }
      if (entries.isEmpty()) {
         findViewById(R.id.tvNoRecords).setVisibility(View.VISIBLE);
         findViewById(R.id.lvForeignAddresses).setVisibility(View.GONE);
      } else {
         findViewById(R.id.tvNoRecords).setVisibility(View.GONE);
         findViewById(R.id.lvForeignAddresses).setVisibility(View.VISIBLE);
         ListView list = (ListView) findViewById(R.id.lvForeignAddresses);
         list.setAdapter(new AddressBookAdapter(getActivity(), R.layout.address_book_my_address_row, entries));
      }
   }

   private void updateUiForeign() {
      Map<Address, String> entries = _mbwManager.getMetadataStorage().getAllAddressLabels();
      if (entries.isEmpty()) {
         findViewById(R.id.tvNoRecords).setVisibility(View.VISIBLE);
         findViewById(R.id.lvForeignAddresses).setVisibility(View.GONE);
      } else {
         findViewById(R.id.tvNoRecords).setVisibility(View.GONE);
         findViewById(R.id.lvForeignAddresses).setVisibility(View.VISIBLE);
         ListView foreignList = (ListView) findViewById(R.id.lvForeignAddresses);
         foreignList.setAdapter(new AddressBookAdapter(getActivity(), R.layout.address_book_foreign_row, entries));
      }
   }

   private boolean isForeign(Entry entry) {
      //if it has an Address as key, it is foreign, our own ones have UUID as key
      return entry.getAddressBookKey() instanceof AddressBookManager.AddressKey;
   }

   @Override
   public void setUserVisibleHint(boolean isVisibleToUser) {
      super.setUserVisibleHint(isVisibleToUser);
      if (!isVisibleToUser) {
         finishActionMode();
      }
   }

   private void finishActionMode() {
      if (currentActionMode != null) {
         currentActionMode.finish();
      }
   }

   OnItemClickListener itemListClickListener = new OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> listView, final View view, int position, long id) {
         mSelectedAddress = (Address) view.getTag();
         ActionBarActivity parent = (ActionBarActivity) getActivity();
         currentActionMode = parent.startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
               actionMode.getMenuInflater().inflate(R.menu.addressbook_context_menu, menu);
               return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
               currentActionMode = actionMode;
               view.setBackgroundDrawable(getResources().getDrawable(R.color.selectedrecord));
               return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
               final int item = menuItem.getItemId();
               if (item == R.id.miDeleteAddress) {
                  _mbwManager.runPinProtectedFunction(getActivity(), pinProtectedDeleteEntry);
                  return true;
               } else if (item == R.id.miEditAddress) {
                  _mbwManager.runPinProtectedFunction(getActivity(), pinProtectedEditEntry);
                  return true;
               } else if (item == R.id.miShowQrCode) {
                  doShowQrCode();
                  return true;
               }
               return false;
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
               view.setBackgroundDrawable(null);
               currentActionMode = null;
            }
         });
      }
   };

   final Runnable pinProtectedEditEntry = new Runnable() {

      @Override
      public void run() {
         doEditEntry();
      }
   };

   private void doEditEntry() {
      EnterAddressLabelUtil.enterAddressLabel(getActivity(), _mbwManager.getMetadataStorage(), mSelectedAddress, "", addressLabelChanged);
   }

   private void doShowQrCode() {
      if (!isAdded()) {
         return;
      }
      if (mSelectedAddress == null) {
         return;
      }
      boolean hasPrivateKey = _mbwManager.getWalletManager(false).hasPrivateKeyForAddress(mSelectedAddress);
      ReceiveCoinsActivity.callMe(getActivity(), mSelectedAddress, hasPrivateKey);
      finishActionMode();
   }

   final Runnable pinProtectedDeleteEntry = new Runnable() {

      @Override
      public void run() {
         doDeleteEntry();
      }
   };

   private void doDeleteEntry() {
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setMessage(R.string.delete_address_confirmation).setCancelable(false)
            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int id) {
                  dialog.cancel();
                  _mbwManager.getMetadataStorage().deleteAddressMetadata(mSelectedAddress);
                  finishActionMode();
                  updateUi();
               }
            }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int id) {
                  finishActionMode();
               }
            });
      AlertDialog alertDialog = builder.create();
      alertDialog.show();
   }

   private class AddressBookAdapter extends ArrayAdapter<Map.Entry<Address, String>> {

      public AddressBookAdapter(Context context, int textViewResourceId, Map<Address, String> entries) {
         super(context, textViewResourceId, new ArrayList(entries.entrySet()));
      }

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
         View v = convertView;

         if (v == null) {
            LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = Preconditions.checkNotNull(vi.inflate(R.layout.address_book_row, null));
         }
         TextView tvName = (TextView) v.findViewById(R.id.address_book_name);
         TextView tvAddress = (TextView) v.findViewById(R.id.address_book_address);
         Map.Entry<Address, String> e = getItem(position);
         tvName.setText(e.getValue());
         String text = e.getKey().toMultiLineString();
         tvAddress.setText(text);
         v.setTag(e.getKey());
         return v;
      }
   }

   private class AddDialog extends Dialog {

      public AddDialog(final Activity activity) {
         super(activity);
         this.setContentView(R.layout.add_to_address_book_dialog);
         this.setTitle(R.string.add_to_address_book_dialog_title);

         findViewById(R.id.btScan).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
               ScanRequest request = ScanRequest.getAddressBookScanRequest();
               ScanActivity.callMe(AddressBookFragment.this, SCAN_RESULT_CODE, request);
               AddDialog.this.dismiss();
            }

         });

         Optional<Address> address = Utils.addressFromString(Utils.getClipboardString(activity), _mbwManager.getNetwork());
         findViewById(R.id.btClipboard).setEnabled(address.isPresent());
         findViewById(R.id.btClipboard).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
               Optional<Address> address = Utils.addressFromString(Utils.getClipboardString(activity), _mbwManager.getNetwork());
               Preconditions.checkState(address.isPresent());
               addFromAddress(address.get());
               AddDialog.this.dismiss();
            }
         });
      }
   }

   public boolean onOptionsItemSelected(MenuItem item) {
      if (item.getItemId() == R.id.miAddAddress) {
         _addDialog = new AddDialog(getActivity());
         _addDialog.show();
         return true;
      }
      return super.onOptionsItemSelected(item);
   }

   @Override
   public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
      if (requestCode != SCAN_RESULT_CODE) {
         super.onActivityResult(requestCode, resultCode, intent);
      }
      if (resultCode != Activity.RESULT_OK) {
         if (intent == null) {
            return; // user pressed back
         }
         String error = intent.getStringExtra(ScanActivity.RESULT_ERROR);
         if (error != null) {
            Toast.makeText(this.getActivity(), error, Toast.LENGTH_LONG).show();
         }
         return;
      }
      ScanActivity.ResultType type = (ScanActivity.ResultType) intent.getSerializableExtra(ScanActivity.RESULT_TYPE_KEY);
      if (type == ScanActivity.ResultType.PRIVATE_KEY) {
         Utils.showSimpleMessageDialog(getActivity(), R.string.addressbook_cannot_add_private_key);
         return;
      }
      Preconditions.checkState(type == ScanActivity.ResultType.ADDRESS);
      Address address = ScanActivity.getAddress(intent);
      addFromAddress(address);
   }

   private void addFromAddress(Address address) {
         EnterAddressLabelUtil.enterAddressLabel(getActivity(), _mbwManager.getMetadataStorage(), address, "", addressLabelChanged);
   }

   private AddressLabelChangedHandler addressLabelChanged = new AddressLabelChangedHandler() {

      @Override
      public void OnAddressLabelChanged(Address address, String label) {
         finishActionMode();
         updateUi();
      }
   };

   @Subscribe
   public void onAddressBookChanged(AddressBookChanged event) {
      updateUi();
   }

   private class SelectItemListener implements OnItemClickListener {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
         Address address = (Address) view.getTag();
         Intent result = new Intent();
         result.putExtra(ADDRESS_RESULT_NAME, address.toString());
         getActivity().setResult(Activity.RESULT_OK, result);
         getActivity().finish();
      }
   }
}
