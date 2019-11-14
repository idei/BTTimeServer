package edu.unsj.idei.btletimeserver;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TextView;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link PeerFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link PeerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PeerFragment extends Fragment
{
	// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
	private static final String ARG_NAME = "name";
	private static final String ARG_ADDRESS = "address";
	private static final String ARG_RSSI = "rssi";

	private String _sName;
	private String _sAddress;
	private int _iRSSI;

	private View view;

	private OnFragmentInteractionListener mListener;

	public PeerFragment()
	{
	}

	/**
	 * Use this factory method to create a new instance of
	 * this fragment using the provided parameters.
	 *
	 * @param deviceName    Parameter 1.
	 * @param deviceAddress Parameter 2.
	 * @return A new instance of fragment PeerFragment.
	 */
	static PeerFragment newInstance(String deviceName, String deviceAddress, int RSSI)
	{
		PeerFragment fragment = new PeerFragment();
		Bundle args = new Bundle();
		args.putString(ARG_NAME, deviceName);
		args.putString(ARG_ADDRESS, deviceAddress);
		args.putInt(ARG_RSSI, RSSI);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (getArguments() != null)
		{
			_sName = getArguments().getString(ARG_NAME);
			_sAddress = getArguments().getString(ARG_ADDRESS);
			_iRSSI = getArguments().getInt(ARG_RSSI);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState)
	{
		// Inflate the layout for this fragment
		view = inflater.inflate(R.layout.fragment_peer, container, false);


		view.setOnTouchListener(new View.OnTouchListener()
		{
			public boolean onTouch(View v, MotionEvent event)
			{
				if (event.getAction() == MotionEvent.ACTION_DOWN)
				{
					setSelectionView();
				}
				v.performClick();
				return true;
			}
		});


		TextView peerName = view.findViewById(R.id.peer_name);
		peerName.setText(_sName);
		setArgAddress(_sAddress);
		setArgRssi(_iRSSI);
		return view;
	}

	private boolean _bSelected = false;

	private void setSelectionView()
	{
		_bSelected = !_bSelected;

		TableLayout table = view.findViewById(R.id.main_fragment);
		if (_bSelected)
		{
			table.setBackgroundResource(R.drawable.nearest_selected);
		} else
		{
			table.setBackgroundResource(R.drawable.shadow);
		}
	}

	void setArgAddress(String address)
	{
		if (view != null)
		{
			TextView peerAddress = view.findViewById(R.id.peer_address);
			peerAddress.setText(address);
		}
	}

	void setArgRssi(int rssi)
	{
		if (view != null)
		{
			TextView peerRSSI = view.findViewById(R.id.peer_rssi);
			peerRSSI.setText(rssi + "");
		}
	}


	// TODO: Rename method, update argument and hook method into UI event
	public void onButtonPressed(Uri uri)
	{
		if (mListener != null)
		{
			mListener.onFragmentInteraction(uri);
		}
	}

	@Override
	public void onAttach(@NonNull Context context)
	{
		super.onAttach(context);
		if (context instanceof OnFragmentInteractionListener)
		{
			mListener = (OnFragmentInteractionListener) context;
		} else
		{
			throw new RuntimeException(context.toString()
					+ " must implement OnFragmentInteractionListener");
		}
	}

	@Override
	public void onDetach()
	{
		super.onDetach();
		mListener = null;
	}

	/**
	 * This interface must be implemented by activities that contain this
	 * fragment to allow an interaction in this fragment to be communicated
	 * to the activity and potentially other fragments contained in that
	 * activity.
	 * <p>
	 * See the Android Training lesson <a href=
	 * "http://developer.android.com/training/basics/fragments/communicating.html"
	 * >Communicating with Other Fragments</a> for more information.
	 */
	public interface OnFragmentInteractionListener
	{
		// TODO: Update argument type and name
		void onFragmentInteraction(Uri uri);
	}
}
