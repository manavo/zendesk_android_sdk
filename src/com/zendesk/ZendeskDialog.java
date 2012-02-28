package com.zendesk;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ZendeskDialog {
	public final String TAG = "Zendesk";
	private final String TITLE_DEFAULT = "Support";
	private final String DESCRIPTION_DEFAULT = "How may we help you? Please fill in details below, and we'll get back to you as soon as possible.";
	private final String TAG_DEFAULT = "dropbox";

	private String email;
	private String title;
	private String description;
	private String url;
	private String tag;

	private Context context;
	private View dialogView;
	private Handler toastHandler;

	private TextView descriptionTV;
	private EditText descriptionET;
	private TextView subjectTV;
	private EditText subjectET;
	private TextView emailTV;
	private EditText emailET;

	private AlertDialog aDialog;
	
	private ProgressDialog progressDialog;
	
	private Handler successCallback = null;

	public ZendeskDialog(Context context) {
		this.context = context;
		dialogView = createDialogView(context);
		aDialog = new AlertDialog.Builder(context).setTitle(TITLE_DEFAULT).setView(dialogView).setPositiveButton("Send", null).setNegativeButton("Cancel", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				resetDialogView();
			}
		}).create();
		
		this.progressDialog = new ProgressDialog(context);
		this.progressDialog.setMessage("Sending...");
		this.progressDialog.setCancelable(false);
	}
	
	public ZendeskDialog setSuccessCallback(Handler h) {
		this.successCallback = h;
		return this;
	}

	public ZendeskDialog setEmail(String email) {
		this.email = email;
		return this;
	}

	public ZendeskDialog setTitle(String title) {
		this.title = title;
		return this;
	}

	public ZendeskDialog setDescription(String description) {
		this.description = description;
		return this;
	}

	public ZendeskDialog setUrl(String url) {
		this.url = url;
		return this;
	}
	
	public ZendeskDialog setTag(String tag) {
		this.tag = tag;
		return this;
	}

	public ZendeskDialog show() {
		// set Dialog Title
		if (this.title != null)
			aDialog.setTitle(this.title);
		else if (getMetaDataByKey("zendesk_title") != null)
			aDialog.setTitle(getMetaDataByKey("zendesk_title"));

		// set Dialog Email value
		if (this.email != null)
			emailET.setText(this.email);

		// set Dialog description
		descriptionTV.setText(DESCRIPTION_DEFAULT);
		if (this.description != null)
			descriptionTV.setText(this.description);
		else if (getMetaDataByKey("zendesk_description") != null)
			descriptionTV.setText(getMetaDataByKey("zendesk_description"));

		if (this.tag == null){ // not already configured programatically
			String tagConfig = getMetaDataByKey("zendesk_tag");
			if (tagConfig != null){
				this.tag = getMetaDataByKey("zendesk_tag");
			}
			else{
				this.tag = TAG_DEFAULT;
			}
		}
		
		// set Dialog url
		if (this.url == null)
			this.url = getMetaDataByKey("zendesk_url");

		if (this.url != null) {
			aDialog.show();
			
			// alter the click listener, so the dialog doesn't close by default
			aDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (descriptionET.length() != 0 && subjectET.length() != 0 && emailET.length() != 0) {
						toastHandler = new Handler() {
							public void handleMessage(Message msg) {
								super.handleMessage(msg);
								// notify to user here
								String message = msg.getData().getString("submit");
								if (message.equals("successfully"))
									message = "Your request has successfully been submitted";
								else
									message = "Your request couldn't be submitted, please try again";
								Toast.makeText(ZendeskDialog.this.context, message, Toast.LENGTH_SHORT).show();
								ZendeskDialog.this.progressDialog.dismiss();
							}
						};
						ZendeskDialog.this.progressDialog.show();
						new Thread(runnable).start();
					} else {
						if (descriptionET.length() == 0)
							descriptionTV.setTextColor(Color.RED);
						else
							descriptionTV.setTextColor(Color.WHITE);
						if (subjectET.length() == 0)
							subjectTV.setTextColor(Color.RED);
						else
							subjectTV.setTextColor(Color.WHITE);
						if (emailET.length() == 0)
							emailTV.setTextColor(Color.RED);
						else
							emailTV.setTextColor(Color.WHITE);

						Toast.makeText(ZendeskDialog.this.context, "Please fill in all the fields", Toast.LENGTH_SHORT).show();
					}
				}
			});
		} else {
			Log.e(TAG, "Meta Data with key \"zendesk_url\" couldn't be found in AndroidManifext.xml");
		}
		return this;
	}

	private String getMetaDataByKey(String key) {
		PackageManager manager = null;
		ApplicationInfo info = null;
		String valueByKey = "";
		try {
			manager = context.getPackageManager();
			info = manager.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
			valueByKey = info.metaData.getString(key);
			Log.d(TAG, "Key: " + key + " - Value: " + valueByKey);
		} catch (Exception e) {
			Log.e(TAG, "Error reading meta data from AndroidManifest.xml", e);
			return null;
		}
		return valueByKey;
	}

	Runnable runnable = new Runnable() {
		public void run() {
			Message message = new Message();
			String description = descriptionET.getText().toString();
			String subject = subjectET.getText().toString();
			String email = emailET.getText().toString();

			// Submit query here
			try {
				String server = ZendeskDialog.this.url;
				String dir = "/requests/mobile_api/create.json";
				String reqDesc = "description=" + URLEncoder.encode(description, "UTF-8");
				String reqEmail = "email=" + URLEncoder.encode(email, "UTF-8");
				String reqSubject = "subject=" + URLEncoder.encode(subject, "UTF-8");
				String reqTag = "set_tags=" + URLEncoder.encode(ZendeskDialog.this.tag, "UTF-8");
				
				String reqContent = reqDesc + "&" + reqEmail + "&" + reqSubject + "&" + reqTag;
				String requestUrl = "http://" + server + dir;

				URL url = new URL(requestUrl);
				Log.d(TAG, "Sending Request " + url.toExternalForm());
				
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("POST");
				connection.setRequestProperty ("Content-Type","application/x-www-form-urlencoded");
				connection.setRequestProperty("Content-Length", "" + Integer.toString(reqContent.getBytes().length));
				connection.addRequestProperty("X-Zendesk-Mobile-API", "1.0");
				connection.setUseCaches(false);
				connection.setDoInput(true);
				connection.setDoOutput(true);

				// send request
				DataOutputStream out = new DataOutputStream(connection.getOutputStream());
				out.writeBytes(reqContent);
				out.flush();
				out.close();

				// get response
				InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
				BufferedReader bufferReader = new BufferedReader(inputStreamReader, 8192);
				String line = "";
				while ((line = bufferReader.readLine()) != null) {
					Log.d(TAG, line);
				}
				bufferReader.close();
	
				aDialog.dismiss();
				resetDialogView();
				
				if (ZendeskDialog.this.successCallback != null) {
					ZendeskDialog.this.successCallback.sendEmptyMessage(0);
				}
			
				message.getData().putString("submit", "successfully");
				toastHandler.sendMessage(message);

			} catch (Exception e) {
				message.getData().putString("submit", "failed");
				toastHandler.sendMessage(message);
				Log.e(TAG, "Error while, submit request", e);
			}
		}
	};

	private View createDialogView(Context context) {
		LinearLayout llRoot = new LinearLayout(context);
		llRoot.setOrientation(LinearLayout.VERTICAL);
		llRoot.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

		ScrollView sv = new ScrollView(context);
		sv.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1));

		LinearLayout llContent = new LinearLayout(context);
		llContent.setOrientation(LinearLayout.VERTICAL);
		llContent.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		llContent.setPadding(10, 0, 10, 0);

		LinearLayout llTop = new LinearLayout(context);
		llTop.setOrientation(LinearLayout.VERTICAL);

		descriptionTV = new TextView(context);
		descriptionTV.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		descriptionTV.setTextColor(Color.WHITE);
		descriptionET = new EditText(context);
		descriptionET.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		descriptionET.setMinLines(3);
		descriptionET.setMaxLines(3);
		descriptionET.setInputType(InputType.TYPE_CLASS_TEXT 
				| InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
				| InputType.TYPE_TEXT_FLAG_AUTO_CORRECT 
				| InputType.TYPE_TEXT_FLAG_MULTI_LINE );
		descriptionET.setGravity(Gravity.TOP);

		subjectTV = new TextView(context);
		subjectTV.setText("Subject:");
		subjectTV.setTextColor(Color.WHITE);
		subjectET = new EditText(context);
		subjectET.setSingleLine(true);
		subjectET.setInputType(InputType.TYPE_CLASS_TEXT 
				| InputType.TYPE_TEXT_FLAG_CAP_WORDS
				| InputType.TYPE_TEXT_FLAG_AUTO_CORRECT );

		emailTV = new TextView(context);
		emailTV.setText("E-Mail:");
		emailTV.setTextColor(Color.WHITE);
		emailET = new EditText(context);
		emailET.setSingleLine(true);
		emailET.setInputType(InputType.TYPE_CLASS_TEXT 
				| InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS );

		LinearLayout llBottom = new LinearLayout(context);
		llBottom.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

		TextView poweredByTV = new TextView(context);
		poweredByTV.setText("Powered By");
		poweredByTV.setPadding(0, 0, 10, 0);
		poweredByTV.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT));
		poweredByTV.setGravity(Gravity.CENTER_VERTICAL);

		ImageView poweredByIV = new ImageView(context);
		InputStream in = ZendeskDialog.class.getResourceAsStream("/com/zendesk/zendesk.png");
		Bitmap poweredBy = BitmapFactory.decodeStream(in);
		poweredByIV.setImageBitmap(poweredBy);

		llRoot.addView(sv);

		sv.addView(llContent);

		llContent.addView(llTop);
		llContent.addView(llBottom);

		llTop.addView(descriptionTV);
		llTop.addView(descriptionET);
		llTop.addView(subjectTV);
		llTop.addView(subjectET);
		llTop.addView(emailTV);
		llTop.addView(emailET);

		llBottom.addView(poweredByTV);
		llBottom.addView(poweredByIV);

		return llRoot;
	}

	private void resetDialogView() {
		descriptionET.setText("");
		subjectET.setText("");
		emailET.setText("");
		descriptionTV.setTextColor(Color.WHITE);
		subjectTV.setTextColor(Color.WHITE);
		emailTV.setTextColor(Color.WHITE);
	}

}
