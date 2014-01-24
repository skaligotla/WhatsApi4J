package net.sumppen.whatsapi4j;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Java adaptation of PHP WhatsAPI by venomous0x
 * {@link https://github.com/venomous0x/WhatsAPI}
 * 
 * @author Kim Lindberg (kim@sumppen.net)
 *
 */
public class WhatsApi {

	private final String MEDIA_FOLDER = "media";                           // The relative folder to store received media files
	private final String PICTURES_FOLDER = "pictures";                     // The relative folder to store picture files
	private final int PORT = 443;                                      // The port of the WhatsApp server.
	private final int TIMEOUT_SEC = 5;                                  // The timeout for the connection with the WhatsApp servers.
	private final String WHATSAPP_CHECK_HOST = "v.whatsapp.net/v2/exist";  // The check credentials host.
	private final String WHATSAPP_GROUP_SERVER = "g.us";                   // The Group server hostname
	private final String WHATSAPP_HOST = "c.whatsapp.net";                 // The hostname of the WhatsApp server.
	private final String WHATSAPP_REGISTER_HOST = "v.whatsapp.net/v2/register"; // The register code host.
	private final String WHATSAPP_REQUEST_HOST = "v.whatsapp.net/v2/code";      // The request code host.
	private final String WHATSAPP_SERVER = "s.whatsapp.net";               // The hostname used to login/send messages.
	private final String WHATSAPP_UPLOAD_HOST = "https://mms.whatsapp.net/client/iphone/upload.php"; // The upload host.
	private final String WHATSAPP_DEVICE = "Android";                      // The device name.
	private final String WHATSAPP_VER = "2.11.151";                // The WhatsApp version.
	private final String WHATSAPP_USER_AGENT = "WhatsApp/2.11.151 Android/4.3 Device/GalaxyS3";// User agent used in request/registration code.

	private final Logger log = Logger.getLogger(WhatsApi.class);
	private String identity;
	private final String name;
	private final String phoneNumber;
	private LoginStatus loginStatus;
	private Socket socket;
	private String password;
	private String[] dictionary = {
			null,null,null,null,
			null,"account","ack","action",
			"active","add","after","ib",
			"all","allow","apple","audio","auth",
			"author","available","bad-protocol",
			"bad-request","before","Bell.caf",
			"body","Boing.caf","cancel","category","challenge",
			"chat","clean","code","composing","config",
			"conflict","contacts","count","create",
			"creation","default","delay","delete",
			"delivered","deny","digest","DIGEST-MD5-1",
			"DIGEST-MD5-2","dirty","elapsed","broadcast",
			"enable","encoding","duplicate","error",
			"event","expiration","expired","fail",
			"failure","false","favorites","feature",
			"features","field","first","free",
			"from","g.us","get","Glass.caf",
			"google","group","groups","g_notify",
			"g_sound","Harp.caf","http://etherx.jabber.org/streams","http://jabber.org/protocol/chatstates",
			"id","image","img","inactive",
			"index","internal-server-error","invalid-mechanism","ip",
			"iq","item","item-not-found","user-not-found",
			"jabber:iq:last","jabber:iq:privacy","jabber:x:delay","jabber:x:event",
			"jid","jid-malformed","kind","last",
			"latitude","lc","leave","leave-all",
			"lg","list","location","longitude",
			"max","max_groups","max_participants","max_subject",
			"mechanism","media","message","message_acks",
			"method","microsoft","missing","modify",
			"mute","name","nokia","none",
			"not-acceptable","not-allowed","not-authorized","notification",
			"notify","off","offline","order",
			"owner","owning","paid","participant",
			"participants","participating","password","paused",
			"picture","pin","ping","platform",
			"pop_mean_time","pop_plus_minus","port","presence",
			"preview","probe","proceed","prop",
			"props","p_o","p_t","query",
			"raw","reason","receipt","receipt_acks",
			"received","registration","relay","remote-server-timeout",
			"remove","Replaced by new connection","request","required",
			"resource","resource-constraint","response","result",
			"retry","rim","s.whatsapp.net","s.us",
			"seconds","server","server-error","service-unavailable",
			"set","show","sid","silent",
			"sound","stamp","unsubscribe","stat",
			"status","stream:error","stream:features","subject",
			"subscribe","success","sync","system-shutdown",
			"s_o","s_t","t","text",
			"timeout","TimePassing.caf","timestamp","to",
			"Tri-tone.caf","true","type","unavailable",
			"uri","url","urn:ietf:params:xml:ns:xmpp-sasl","urn:ietf:params:xml:ns:xmpp-stanzas",
			"urn:ietf:params:xml:ns:xmpp-streams","urn:xmpp:delay","urn:xmpp:ping","urn:xmpp:receipts",
			"urn:xmpp:whatsapp","urn:xmpp:whatsapp:account","urn:xmpp:whatsapp:dirty","urn:xmpp:whatsapp:mms",
			"urn:xmpp:whatsapp:push","user","username","value",
			"vcard","version","video","w",
			"w:g","w:p","w:p:r","w:profile:picture",
			"wait","x","xml-not-well-formed","xmlns",
			"xmlns:stream","Xylophone.caf","1","WAUTH-1",
			null,null,null,null,
			null,null,null,null,
			null,null,null,"XXX"
	};
	private BinTreeNodeWriter writer;
	private byte[] challengeData;
	private BinTreeNodeReader reader;
	private KeyStream inputKey;
	private KeyStream outputKey;
	private String serverReceivedId;
	private final List<ProtocolNode> messageQueue = new LinkedList<ProtocolNode>();
	private String lastId;
	private List<ProtocolNode> outQueue = new LinkedList<ProtocolNode>();
	private final EventManager eventManager = new EventManager();
	private int messageCounter = 0;
	private final List<Country> countries;

	public WhatsApi(String username, String identity, String nickname) throws NoSuchAlgorithmException, WhatsAppException {
		writer = new BinTreeNodeWriter(dictionary);
		reader = new BinTreeNodeReader(dictionary);
		this.name = nickname;
		this.phoneNumber = username;
		if(!checkIdentity(identity)) {
			this.identity = buildIdentity();
		} else {
			this.identity = identity;
		}
		this.loginStatus = LoginStatus.DISCONNECTED_STATUS;
		countries = readCountries();
	}

	private List<Country> readCountries() throws WhatsAppException {
		List<Country> result = new LinkedList<Country>();
		InputStream is = this.getClass().getResourceAsStream("/countries.csv");
		BufferedReader br = null;
		String line = "";
		String cvsSplitBy = ",";
		if(is == null) {
			throw new WhatsAppException("Failed to locate countries.csv");
		}
	 
		try {
	 
			br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			while ((line = br.readLine()) != null) {
	 
				// use comma as separator
				String[] entry = line.split(cvsSplitBy);
				Country country = new Country(entry);
				result.add(country);
			}
	 
		} catch (FileNotFoundException e) {
			log.warn(e);
		} catch (IOException e) {
			log.warn(e);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					log.warn(e);
				}
			}
			if(is != null) {
				try {
					is.close();
				} catch (IOException e) {
					log.warn(e);
				}
			}
		}		
		return result;
	}

	protected List<Country> getCountries() {
		return countries;
	}
	
	private String buildIdentity() {
		// TODO Auto-generated method stub
		return null;
	}

	private boolean checkIdentity(String id) {
		// TODO Auto-generated method stub
		return true;
	}

	public boolean connect() throws UnknownHostException, IOException {
		socket = new Socket(WHATSAPP_HOST, PORT);
		if(socket.isConnected()) {
			socket.setSoTimeout(TIMEOUT_SEC*1000);
			return true;
		} else {
			log.warn("Failed to connect to WhatsApp server");
			return false;
		}	
	}

	public void loginWithPassword(String password) throws WhatsAppException {
		this.password = password;
		try {
			doLogin();
			if(loginStatus != LoginStatus.CONNECTED_STATUS) {
				throw new WhatsAppException("Failed to log in");
			}
		} catch (Exception e) {
			throw new WhatsAppException(e);
		}
	}

	private void doLogin() throws IOException, IncompleteMessageException, InvalidMessageException, InvalidTokenException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, WhatsAppException {
		writer.resetKey();
		reader.resetKey();
		String resource = WHATSAPP_DEVICE + "-" + WHATSAPP_VER + "-" + PORT;
		byte[] data = writer.startStream(WHATSAPP_SERVER, resource);
		ProtocolNode feat = createFeaturesNode(false);
		ProtocolNode auth = createAuthNode();
		sendData(data);
		sendNode(feat);
		sendNode(auth);

		processInboundData(readData());

		if(loginStatus != LoginStatus.CONNECTED_STATUS) {
			ProtocolNode dataNode = createAuthResponseNode();
			sendNode(dataNode);
			reader.setKey(inputKey);
			writer.setKey(outputKey);
		}
		int cnt = 0;
		do {
			processInboundData(readData());
		} while ((cnt++ < 100) && (loginStatus == LoginStatus.DISCONNECTED_STATUS));
		sendPresence("available");
	}

	private void sendPresence(String type) throws IOException, WhatsAppException {
		Map<String, String> presence = new LinkedHashMap<String, String>();
		presence.put("type",type);
		presence.put("name",name);
		ProtocolNode node = new ProtocolNode("presence", presence, null, null);
		sendNode(node);
		eventManager().fireSendPresence(
				phoneNumber, 
				presence.get("type"), 
				presence.get("name")
				);
	}

	/**
	 * Add the auth response to protocoltreenode.
	 *
	 * @return ProtocolNode
	 *   Return itself.
	 * @throws IOException 
	 * @throws InvalidKeySpecException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 */
	private ProtocolNode createAuthResponseNode() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, InvalidKeyException {
		byte[] resp = authenticate();
		Map<String,String> attributes = new LinkedHashMap<String, String>();
		attributes.put("xmlns","urn:ietf:params:xml:ns:xmpp-sasl");
		ProtocolNode node = new ProtocolNode("response", attributes, null, resp);

		return node;
	}

	/**
	 * Authenticate with the Whatsapp Server.
	 *
	 * @return byte[]
	 *   Returns binary string
	 * @throws InvalidKeySpecException 
	 * @throws NoSuchAlgorithmException 
	 * @throws IOException 
	 * @throws InvalidKeyException 
	 */
	private byte[] authenticate() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, InvalidKeyException {
		byte[] key = pbkdf2("SHA-1", base64_decode(password), getChallengeData(), 16, 20,true);
		inputKey = new KeyStream(key);
		outputKey = new KeyStream(key);

		ByteArrayOutputStream array = new ByteArrayOutputStream();
		array.write(phoneNumber.getBytes()); 
		array.write(challengeData);
		array.write(Long.toString((new Date()).getTime()/1000).getBytes());
		log.debug("array: "+toHex(array.toByteArray()));
		byte[] response = outputKey.encode(array.toByteArray(), 0, array.size(), false);
		log.debug("response: "+toHex(response));
		return response;
	}

	private byte[] getChallengeData() throws NoSuchAlgorithmException {
		if(challengeData == null) {
			log.info("Challenge data is missing!");
			SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
			challengeData = new byte[20];
			sr.nextBytes(challengeData);		 
		}
		return challengeData;
	}

	protected byte[] pbkdf2(String algo, byte[] password,
			byte[] salt, int iterations, int length, boolean raw) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, InvalidKeyException {
		if (iterations <= 0 || length <= 0) {
			throw new InvalidKeySpecException("PBKDF2 ERROR: Invalid parameters.");
		}

		int hash_length = 20; //hash(algo, "", true).length();
		double block_count = Math.ceil(length / hash_length);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		for (int i = 1; i <= block_count; i++) {
			ByteArrayOutputStream last = new ByteArrayOutputStream();
			last.write(salt);
			ByteBuffer buffer = ByteBuffer.allocate(4);
			buffer.putInt(i);
			last.write(buffer.array());
			byte[] lastBuf = last.toByteArray();
			byte[] xorsum = KeyStream.hash_hmac(lastBuf, password);
			byte[] xorsum2 = xorsum;
			for (int j = 1; j < iterations; j++) {
				xorsum2 = KeyStream.hash_hmac(xorsum2, password);
				last.reset();
				int k=0;
				for(byte b : xorsum) {
					last.write(b ^ xorsum2[k++]);
				}
				xorsum = last.toByteArray();
			}
			output.write(xorsum);
		}
		if(raw) {
			return output.toByteArray();
		}
		return toHex(output.toByteArray()).getBytes();
	}

	public static String toHex(byte[] array) throws NoSuchAlgorithmException
	{
		BigInteger bi = new BigInteger(1, array);
		String hex = bi.toString(16);
		int paddingLength = (array.length * 2) - hex.length();
		if(paddingLength > 0)
		{
			return String.format("%0"  +paddingLength + "d", 0) + hex;
		}else{
			return hex;
		}
	}

	byte[] base64_decode(String pwd) {
		return org.apache.commons.codec.binary.Base64.decodeBase64(pwd.getBytes());

	}

	private void processInboundData(byte[] readData) throws IncompleteMessageException, InvalidMessageException, InvalidTokenException, IOException, WhatsAppException {
		ProtocolNode node = reader.nextTree(readData);
		if(node != null) {
			processInboundDataNode(node);
		}
	}

	/**
	 * Will process the data from the server after it's been decrypted and parsed.
	 * 
	 * This also provides a convenient method to use to unit test the event framework.
	 * @throws IOException 
	 * @throws InvalidTokenException 
	 * @throws InvalidMessageException 
	 * @throws IncompleteMessageException 
	 * @throws WhatsAppException 
	 * 
	 */
	private void processInboundDataNode(ProtocolNode node) throws IncompleteMessageException, InvalidMessageException, InvalidTokenException, IOException, WhatsAppException {
		while (node != null) {
			ProtocolTag tag;
			try {
				tag = ProtocolTag.valueOf(node.getTag().replace(':', '_').toUpperCase());
			} catch (IllegalArgumentException e) {
				tag = ProtocolTag.UNKNOWN;
				log.info("Unknown/Unused tag "+node.getTag());
			}
			log.debug("rx  "+node);
			switch(tag) {
			case CHALLENGE:
				processChallenge(node);
				break;
			case SUCCESS:
				loginStatus = LoginStatus.CONNECTED_STATUS;
				challengeData = node.getData();
				file_put_contents("nextChallenge.dat", challengeData);
				writer.setKey(outputKey);
				break;
			case FAILURE:
				log.error("Failure");
				break;
			case MESSAGE:
				processMessage(node);
				break;
			case PRESENCE:
				processPresence(node);
				break;
			case IQ:
				processIq(node);
			case STREAM_ERROR:
				throw new WhatsAppException("stream:error received: ");
			case PING:
				break;
			case QUERY:
				break;
			case START:
				break;
			case UNKNOWN:
				break;
			default:
				break;
			}
			node = reader.nextTree(null);
		}
	}

	private void processIq(ProtocolNode node) throws IOException, WhatsAppException {
		if (node.getAttribute("type").equals("get")
				&& ProtocolTag.valueOf(node.getChild(0).getTag()) == ProtocolTag.PING) {
			eventManager().firePing(
					phoneNumber,
					node.getAttribute("id")
					);
			sendPong(node.getAttribute("id"));
		}
		if (node.getAttribute("type").equals("result")) {
			if(log.isDebugEnabled()) {
				log.debug("processIq: setting received id to "+node.getAttribute("id"));
			}
			serverReceivedId = node.getAttribute("id");
			if (node.getChild(0) != null &&
					node.getChild(0).getTag().equals(ProtocolTag.QUERY)) {
				if (node.getChild(0).getAttribute("xmlns").equals("jabber:iq:privacy")) {
					// ToDo: We need to get explicitly list out the children as arguments
					//       here.
					eventManager().fireGetPrivacyBlockedList(
							phoneNumber,
							node.getChild(0).getChild(0).getChildren()
							);
				}
				if (node.getChild(0).getAttribute("xmlns").equals("jabber:iq:last")) {
					eventManager().fireGetRequestLastSeen(
							phoneNumber,
							node.getAttribute("from"),
							node.getAttribute("id"),
							node.getChild(0).getAttribute("seconds")
							);				}
			}
			messageQueue.add(node);
		}
		if (node.getChild(0) != null && node.getChild(0).getTag().equals("props")) {
			//server properties
			Map<String,String> props = new LinkedHashMap<String,String>();
			for(ProtocolNode child : node.getChild(0).getChildren()) {
				props.put(child.getAttribute("name"),child.getAttribute("value"));
			}
			eventManager().fireGetServerProperties(
					phoneNumber,
					node.getChild(0).getAttribute("version"),
					props
					);
		}
		if (node.getChild(0) != null && node.getChild(0).getTag().equals("picture")) {
			eventManager().fireGetProfilePicture(
					phoneNumber,
					node.getAttribute("from"),
					node.getChild("picture").getAttribute("type"),
					node.getChild("picture").getData()
					);
		}
		if (node.getChild(0) != null && node.getChild(0).getTag().equals("media")) {
			processUploadResponse(node);
		}
		if (node.getChild(0) != null && node.getChild(0).getTag().equals("duplicate")) {
			processUploadResponse(node);
		}
		if (node.nodeIdContains("group")) {
			//There are multiple types of Group reponses. Also a valid group response can have NO children.
			//Events fired depend on text in the ID field.
			Map<String,String> groupList = new LinkedHashMap<String,String>();
			String groupId = null;
			if (node.getChild(0) != null) {
				for(ProtocolNode child : node.getChildren()) {
					groupList.putAll(child.getAttributes());
				}
			}
			if(node.nodeIdContains("creategroup")){
				groupId = node.getChild(0).getAttribute("id");
				eventManager().fireGroupsChatCreate(
						phoneNumber,
						groupId
						);
			}
			if(node.nodeIdContains("endgroup")){
				groupId = node.getChild(0).getChild(0).getAttribute("id");
				eventManager().fireGroupsChatEnd(
						phoneNumber,
						groupId
						);
			}
			if(node.nodeIdContains("getgroups")){
				eventManager().fireGetGroups(
						phoneNumber,
						groupList
						);
			}
			if(node.nodeIdContains("getgroupinfo")){
				eventManager().fireGetGroupsInfo(
						phoneNumber,
						groupList
						);
			}
			if(node.nodeIdContains("getgroupparticipants")){
				groupId = parseJID(node.getAttribute("from"));
				eventManager().fireGetGroupParticipants(
						phoneNumber,
						groupId,
						groupList
						);
			}

		}
		if (node.getTag().equals("iq") && node.getAttribute("type").equals("error")) {
			serverReceivedId = node.getAttribute("id");
		}

	}

	/**
	 * Process media upload response
	 *
	 * @param ProtocolNode $node
	 *  Message node
	 * @return bool
	 */
	private boolean processUploadResponse(ProtocolNode node) {
		String id = node.getAttribute("id");
		return false;
		/* TODO
        $messageNode = mediaQueue[$id];
        if ($messageNode == null) {
            //message not found, can't send!
            $this->eventManager()->fireMediaUploadFailed(
                $this->phoneNumber,
                $id,
                $node,
                $messageNode,
                "Message node not found in queue"
            );
            return false;
        }

        $duplicate = node.getChild("duplicate");
        if ($duplicate != null) {
            //file already on whatsapp servers
            $url = $duplicate->getAttribute("url");
            $filesize = $duplicate->getAttribute("size");
//            $mimetype = $duplicate->getAttribute("mimetype");
//            $filehash = $duplicate->getAttribute("filehash");
            $filetype = $duplicate->getAttribute("type");
//            $width = $duplicate->getAttribute("width");
//            $height = $duplicate->getAttribute("height");
            $exploded = explode("/", $url);  
            $filename = array_pop($exploded);
        } else {
            //upload new file
            $json = WhatsMediaUploader::pushFile($node, $messageNode, $this->mediaFileInfo, $this->phoneNumber);

            if (!$json) {
                //failed upload
                $this->eventManager()->fireMediaUploadFailed(
                    $this->phoneNumber,
                    $id,
                    $node,
                    $messageNode,
                    "Failed to push file to server"
                );
                return false;
            }

            $url = $json->url;
            $filesize = $json->size;
//            $mimetype = $json->mimetype;
//            $filehash = $json->filehash;
            $filetype = $json->type;
//            $width = $json->width;
//            $height = $json->height;
            $filename = $json->name;
        }

        $mediaAttribs = array();
        $mediaAttribs["xmlns"] = "urn:xmpp:whatsapp:mms";
        $mediaAttribs["type"] = $filetype;
        $mediaAttribs["url"] = $url;
        $mediaAttribs["file"] = $filename;
        $mediaAttribs["size"] = $filesize;

        $filepath = $this->mediaQueue[$id]['filePath'];
        $to = $this->mediaQueue[$id]['to'];

        switch ($filetype) {
            case "image":
                $icon = createIcon($filepath);
                break;
            case "video":
                $icon = videoThumbnail();
                break;
            default:
                $icon = '';
                break;
        }

        $mediaNode = new ProtocolNode("media", $mediaAttribs, null, $icon);
        if (is_array($to)) {
            $this->sendBroadcast($to, $mediaNode);
        } else {
            $this->sendMessageNode($to, $mediaNode);
        }
        $this->eventManager()->fireMediaMessageSent(
            $this->phoneNumber,
            $to,
            $id,
            $filetype,
            $url,
            $filename,
            $filesize,
            $icon
        );
        return true;
		 */
	}

	private void sendPong(String msgid) throws IOException, WhatsAppException {
		Map<String,String> messageHash = new LinkedHashMap<String,String>();
		messageHash.put("to",WHATSAPP_SERVER);
		messageHash.put("id",msgid);
		messageHash.put("type","result");

		ProtocolNode messageNode = new ProtocolNode("iq", messageHash, null, null);
		sendNode(messageNode);
		eventManager().fireSendPong(
				phoneNumber, 
				msgid
				);
	}

	private EventManager eventManager() {
		return eventManager ;
	}

	private void file_put_contents(String string, Object challengeData2) {
		// TODO Auto-generated method stub

	}

	private void processChallenge(ProtocolNode node) {
		log.debug("processChallenge: "+node.getData().length);
		challengeData = node.getData();
	}

	private void processPresence(ProtocolNode node) {
		if (node.getAttribute("status") != null && node.getAttribute("status").equals("dirty")) {
			//clear dirty
			List<String> categories = new LinkedList<String>();
			if (node.getChildren() != null && node.getChildren().size() > 0) {
				for(ProtocolNode child : node.getChildren()) {
					if (child.getTag().equals("category")) {
						categories.add(child.getAttribute("name"));
					}
				}
			}
			sendClearDirty(categories);
		}
		String from = node.getAttribute("from");
		String type = node.getAttribute("type");
		if(from != null && type != null) {
			if (from.startsWith(phoneNumber) 
					&& !from.contains("-")) {
				eventManager().firePresence(
						phoneNumber,
						from,
						type
						);
			}
			if(!from.startsWith(phoneNumber)
					&& from.contains("-")) {
				String groupId = parseJID(from);
				if (node.getAttribute("add") != null) {
					eventManager().fireGroupsParticipantsAdd(
							phoneNumber,
							groupId,
							parseJID(node.getAttribute("add"))
							);
				} else {
					if (node.getAttribute("remove") != null) {
						eventManager().fireGroupsParticipantsRemove(
								phoneNumber,
								groupId,
								parseJID(node.getAttribute("remove")),
								parseJID(node.getAttribute("author"))
								);
					}
				}
			}
		}		
	}

	private String parseJID(String attribute) {
		// TODO Auto-generated method stub
		return null;
	}

	private void sendClearDirty(List<String> categories) {
		// TODO Auto-generated method stub

	}

	private void processMessage(ProtocolNode node) throws IOException, WhatsAppException {
		log.debug("processMessage:");
		messageQueue.add(node);

		//do not send received confirmation if sender is yourself
		if (!node.getAttribute("from").contains(phoneNumber + "@" + WHATSAPP_SERVER)
				&& (node.hasChild("request") || node.hasChild("received"))) {
			sendMessageReceived(node);
		}

		// check if it is a response to a status request
		String[] foo = node.getAttribute("from").split("@");
		if (foo.length > 1 && foo[1].equals("s.us") && node.getChild("body") != null) {
			eventManager().fireGetStatus(
					phoneNumber,
					node.getAttribute("from"),
					node.getAttribute("type"),
					node.getAttribute("id"),
					node.getAttribute("t"),
					node.getChild("body").getData()
					);
		}
		if (node.hasChild("x") && lastId.equals(node.getAttribute("id"))) {
			sendNextMessage();
		}
		// TODO What is this???
		//        if (newMsgBind!=null && node.hasChild("body")) {
		//            newMsgBind.process(node);
		//        }

		if (node.hasChild("composing")) {
			eventManager().fireMessageComposing(
					phoneNumber,
					node.getAttribute("from"),
					node.getAttribute("id"),
					node.getAttribute("type"),
					node.getAttribute("t")
					);
		}
		if (node.hasChild("paused")) {
			eventManager().fireMessagePaused(
					phoneNumber,
					node.getAttribute("from"),
					node.getAttribute("type"),
					node.getAttribute("id"),
					node.getAttribute("t")
					);
		}
		if (node.hasChild("notify") && node.getChild(0).getAttribute("name") != null &&
				node.getChild(0).getAttribute("name").length() < 1 && node.getChild("body") != null) {
			String author = node.getAttribute("author");
			if(author == null || author.length() < 1)
			{
				//private chat message
				eventManager().fireGetMessage(
						phoneNumber,
						node.getAttribute("from"),
						node.getAttribute("id"),
						node.getAttribute("type"),
						node.getAttribute("t"),
						node.getChild("notify").getAttribute("name"),
						node.getChild("body").getData()
						);
			}
			else
			{
				//group chat message
				eventManager().fireGetGroupMessage(
						phoneNumber,
						node.getAttribute("from"),
						author,
						node.getAttribute("id"),
						node.getAttribute("type"),
						node.getAttribute("t"),
						node.getChild("notify").getAttribute("name"),
						node.getChild("body").getData()
						);
			}
		}
		if (node.hasChild("notification") && node.getChild("notification").getAttribute("type").equals("picture")) {
			if (node.getChild("notification").hasChild("set")) {
				eventManager().fireProfilePictureChanged(
						phoneNumber,
						node.getAttribute("from"),
						node.getAttribute("id"),
						node.getAttribute("t")
						);
			} else if (node.getChild("notification").hasChild("delete")) {
				eventManager().fireProfilePictureDeleted(
						phoneNumber,
						node.getAttribute("from"),
						node.getAttribute("id"),
						node.getAttribute("t")
						);
			}
		}
		if (node.getChild("notify") != null && node.getChild(0).getAttribute("name") != null && node.getChild("media") != null) {
			if (node.getChild(2).getAttribute("type") == "image") {
				eventManager().fireGetImage(
						phoneNumber,
						node.getAttribute("from"),
						node.getAttribute("id"),
						node.getAttribute("type"),
						node.getAttribute("t"),
						node.getChild(0).getAttribute("name"),
						node.getChild(2).getAttribute("size"),
						node.getChild(2).getAttribute("url"),
						node.getChild(2).getAttribute("file"),
						node.getChild(2).getAttribute("mimetype"),
						node.getChild(2).getAttribute("filehash"),
						node.getChild(2).getAttribute("width"),
						node.getChild(2).getAttribute("height"),
						node.getChild(2).getData()
						);
			} 
			if (node.getChild(2).getAttribute("type") == "video") {
				eventManager().fireGetVideo(
						phoneNumber,
						node.getAttribute("from"),
						node.getAttribute("id"),
						node.getAttribute("type"),
						node.getAttribute("t"),
						node.getChild(0).getAttribute("name"),
						node.getChild(2).getAttribute("url"),
						node.getChild(2).getAttribute("file"),
						node.getChild(2).getAttribute("size"),
						node.getChild(2).getAttribute("mimetype"),
						node.getChild(2).getAttribute("filehash"),
						node.getChild(2).getAttribute("duration"),
						node.getChild(2).getAttribute("vcodec"),
						node.getChild(2).getAttribute("acodec"),
						node.getChild(2).getData()
						);
			} else
				if (node.getChild(2).getAttribute("type") == "audio") {
					eventManager().fireGetAudio(
							phoneNumber,
							node.getAttribute("from"),
							node.getAttribute("id"),
							node.getAttribute("type"),
							node.getAttribute("t"),
							node.getChild(0).getAttribute("name"),
							node.getChild(2).getAttribute("size"),
							node.getChild(2).getAttribute("url"),
							node.getChild(2).getAttribute("file"),
							node.getChild(2).getAttribute("mimetype"),
							node.getChild(2).getAttribute("filehash"),
							node.getChild(2).getAttribute("duration"),
							node.getChild(2).getAttribute("acodec")
							);
				} 
			if (node.getChild(2).getAttribute("type") == "vcard") {
				eventManager().fireGetvCard(
						phoneNumber,
						node.getAttribute("from"),
						node.getAttribute("id"),
						node.getAttribute("type"),
						node.getAttribute("t"),
						node.getChild(0).getAttribute("name"),
						node.getChild(2).getChild(0).getAttribute("name"),
						node.getChild(2).getChild(0).getData()
						);
			} 
			if (node.getChild(2).getAttribute("type") == "location") {
				String url = node.getChild(2).getAttribute("url");
				String name = node.getChild(2).getAttribute("name");
				eventManager().fireGetLocation(
						phoneNumber,
						node.getAttribute("from"),
						node.getAttribute("id"),
						node.getAttribute("type"),
						node.getAttribute("t"),
						node.getChild(0).getAttribute("name"),
						name,
						node.getChild(2).getAttribute("longitude"),
						node.getChild(2).getAttribute("latitude"),
						url,
						node.getChild(2).getData()
						);
			}
		}
		if (node.getChild("x") != null) {
			if(log.isDebugEnabled()) {
				log.debug("processMessage: setting received id to "+node.getAttribute("id"));
			}
			serverReceivedId = node.getAttribute("id");
			eventManager().fireMessageReceivedServer(
					phoneNumber,
					node.getAttribute("from"),
					node.getAttribute("id"),
					node.getAttribute("type"),
					node.getAttribute("t")
					);
		}
		if (node.getChild("received") != null) {
			eventManager().fireMessageReceivedClient(
					phoneNumber,
					node.getAttribute("from"),
					node.getAttribute("id"),
					node.getAttribute("type"),
					node.getAttribute("t")
					);
		}
		if (node.getAttribute("type").equals("subject")) {
			log.debug(node);
			String[] reset_from = node.getAttribute("from").split("@");
			String[] reset_author = node.getAttribute("author").split("@");
			eventManager().fireGetGroupsSubject(
					phoneNumber,
					reset_from,
					node.getAttribute("t"),
					reset_author,
					reset_author,
					node.getChild(0).getAttribute("name"),
					node.getChild(2).getData()
					);
		}
	}

	private void sendNextMessage() throws IOException, WhatsAppException {
		if (outQueue.size() > 0) {
			ProtocolNode msgnode = outQueue.remove(0);
			msgnode.refreshTimes();
			lastId = msgnode.getAttribute("id");
			sendNode(msgnode);
		} else {
			lastId = null;
		}
	}

	private void sendMessageReceived(ProtocolNode msg) throws IOException, WhatsAppException {
		ProtocolNode requestNode = msg.getChild("request");
		ProtocolNode receivedNode = msg.getChild("received");
		if (requestNode != null || receivedNode != null) {
			Map<String,String> receivedHash = new LinkedHashMap<String, String>();
			receivedHash.put("xmlns","urn:xmpp:receipts");

			String response = "received";
			if(receivedNode != null)
			{
				response = "ack";
			}

			receivedNode = new ProtocolNode(response, receivedHash, null, null);

			Map<String,String> messageHash = new LinkedHashMap<String, String>();
			messageHash.put("to",msg.getAttribute("from"));
			messageHash.put("type","chat");
			messageHash.put("id",msg.getAttribute("id"));
			messageHash.put("t",Long.toString(new Date().getTime()));
			LinkedList<ProtocolNode> receivedList = new LinkedList<ProtocolNode>();
			receivedList.add(receivedNode);
			ProtocolNode messageNode = new ProtocolNode("message", messageHash, receivedList, null);
			sendNode(messageNode);
			eventManager().fireSendMessageReceived(
					phoneNumber, 
					messageHash.get("t"), 
					msg.getAttribute("from")
					);
		}
	}

	private byte[] readData() throws IOException {
		byte[] buf = null;
		if(socket != null && socket.isConnected()) {
			InputStream stream = socket.getInputStream();
			buf = new byte[1042];
			int ret = stream.read(buf);
			if(ret > 0) {

			} else {
				if(ret == -1) {
					log.error("socket EOF, closing socket...");
					socket.close();
					socket = null;
				}
			}
		}
		return buf;
	}

	private void sendNode(ProtocolNode node) throws WhatsAppException {
		try {
			byte[] data = writer.write(node);
			log.debug("tx: "+node.toString());
			sendData(data);
		} catch (Exception e) {
			throw new WhatsAppException("Failed to send node", e);
		}
	}

	private void sendData(byte[] data) throws IOException {
		if(socket != null && socket.isConnected()) {
			if(log.isDebugEnabled()) {
				try {
					log.debug("sendData: "+toHex(data));
				} catch (NoSuchAlgorithmException e) {
				}
			}
			socket.getOutputStream().write(data);
		}
	}

	/**
	 * Add the authentication nodes.
	 *
	 * @return ProtocolNode
	 *   Return itself.
	 * @throws IOException 
	 * @throws InvalidKeySpecException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 */
	private ProtocolNode createAuthNode() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, InvalidKeyException
	{
		Map<String, String> attributes = new LinkedHashMap<String, String>();
		attributes.put("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
		attributes.put("mechanism", "WAUTH-1");
		attributes.put("user",phoneNumber);
		byte[] data;
		data = createAuthBlob();
		ProtocolNode node = new ProtocolNode("auth", attributes, null, data);

		return node;
	}


	private byte[] createAuthBlob() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, InvalidKeyException {
		if(challengeData != null) {
			// TODO
			byte[] key = pbkdf2("PBKDF2WithHmacSHA1", base64_decode(password), challengeData, 16, 20, true);
			inputKey = new KeyStream(key);
			outputKey = new KeyStream(key);
			reader.setKey(inputKey);
			writer.setKey(outputKey);
			Map<String, String> phone = dissectPhone();
			ByteArrayOutputStream array = new ByteArrayOutputStream(); 
			array.write(phoneNumber.getBytes());
			array.write(challengeData);
			array.write(time().getBytes());
			array.write(WHATSAPP_USER_AGENT.getBytes());
			array.write(" MccMnc/".getBytes());
			array.write(phone.get("mcc").getBytes());
			array.write("001".getBytes());
			log.debug("createAuthBlog: challengeData="+toHex(challengeData));
			log.debug("createAuthBlog: array="+toHex(array.toByteArray()));
			challengeData = null;
			return outputKey.encode(array.toByteArray(), 0, array.size(), false);
		}
		return null;	
	}
	/**
	 * Dissect country code from phone number.
	 *
	 * @return map
	 *   An associative map with country code and phone number.
	 *   - country: The detected country name.
	 *   - cc: The detected country code (phone prefix).
	 *   - phone: The phone number.
	 *   - ISO3166: 2-Letter country code
	 *   - ISO639: 2-Letter language code
	 *   Return null if country code is not found.
	 */
	private Map<String,String> dissectPhone() {
		Map<String,String> ret = new LinkedHashMap<String, String>();
		for(Country country : countries) {
			if(phoneNumber.startsWith(country.getCountryCode())) {
				ret.put("country", country.getName());
				ret.put("cc", country.getCountryCode());
				ret.put("phone", phoneNumber.substring(country.getCountryCode().length()));
				ret.put("mcc", country.getMcc());
				ret.put("ISO3166", country.getIso3166());
				ret.put("ISO639", country.getIso639());
				return ret;
			}
		}
		return null;
	}

	private String time() {
		Date now = new Date();

		return Long.toString(now.getTime()/1000);
	}

	/**
	 * Add stream features.
	 * @param bool $profileSubscribe
	 *
	 * @return ProtocolNode
	 *   Return itself.
	 */
	private ProtocolNode createFeaturesNode(boolean profileSubscribe) {
		LinkedList<ProtocolNode> nodes = new LinkedList<ProtocolNode>();
		ProtocolNode receiptAcks = new ProtocolNode("receipt_acks", null, null, null);
		nodes.add(receiptAcks);
		if (profileSubscribe) {
			Map<String, String> attributes = new LinkedHashMap<String, String>();
			attributes.put("type", "all");
			ProtocolNode profile = new ProtocolNode("w:profile:picture", attributes, null, null);
			nodes.add(profile);
		}
		ProtocolNode status = new ProtocolNode("status", null, null, null);
		nodes.add(status);
		ProtocolNode parent = new ProtocolNode("stream:features", null, nodes, null);

		return parent;
	}

	/**
	 * Check if account credentials are valid.
	 *
	 * WARNING: WhatsApp now changes your password everytime you use this.
	 * Make sure you update your config file if the output informs about
	 * a password change.
	 *
	 * @return object
	 *   An object with server response.
	 *   - status: Account status.
	 *   - login: Phone number with country code.
	 *   - pw: Account password.
	 *   - type: Type of account.
	 *   - expiration: Expiration date in UNIX TimeStamp.
	 *   - kind: Kind of account.
	 *   - price: Formatted price of account.
	 *   - cost: Decimal amount of account.
	 *   - currency: Currency price of account.
	 *   - price_expiration: Price expiration in UNIX TimeStamp.
	 * @throws JSONException 
	 * @throws WhatsAppException 
	 *
	 * @throws Exception
	 */
	public boolean checkCredentials() throws JSONException, WhatsAppException {
		//        if (!phone = dissectPhone()) {
		//            throw new Exception("The prived phone number is not valid.");
		//        }

		// Build the url.
		String host = "https://"+WHATSAPP_CHECK_HOST;
		Map<String,String> query = new LinkedHashMap<String, String>();
		query.put("cc","358"); //$phone['cc'],
		query.put("in","401965617"); //=> $phone['phone'],
		query.put("id",identity);
		query.put("c","cookie");

		JSONObject response = getResponse(host, query);
		log.debug(response);
		if (!response.getString("status").equals("ok")) {
			throw new WhatsAppException("There was a problem trying to request the code. Status="+response.getString("status"));
		} else {
			log.debug("Setting password: "+response.getString("pw"));
			password = response.getString("pw");
			return true;
		}
	}

	private JSONObject getResponse(String host, Map<String,String> query) throws JSONException {
		Client client = ClientBuilder.newClient();
		StringBuilder url = new StringBuilder();
		url.append(host);
		String delimiter = "?";
		for(String key : query.keySet()) {
			url.append(delimiter);
			url.append(key);
			url.append("=");
			url.append(query.get(key));
			delimiter = "&";
		}
		String resp = client.target(url.toString()).request().get(String.class);
		return new JSONObject(resp);
	}

	public String sendMessage(String to, String message) throws WhatsAppException {
		return sendMessage(to, message, null);
	}

	public String sendMessage(String to, String message, String id) throws WhatsAppException {
		message = parseMessageForEmojis(message);
		ProtocolNode bodyNode = new ProtocolNode("body", null, null, message.getBytes());
		try {
			return sendMessageNode(to, bodyNode, id);
		} catch (Exception e) {
			throw new WhatsAppException("Failed to send message",e);
		}
	}

	/**
	 * Send node to the servers.
	 *
	 * @param to
	 *   The recipient to send.
	 * @param node
	 *   The node that contains the message.
	 *   @return message id
	 * @throws IOException 
	 * @throws InvalidTokenException 
	 * @throws InvalidMessageException 
	 * @throws IncompleteMessageException 
	 * @throws WhatsAppException 
	 */
	private String sendMessageNode(String to, ProtocolNode node, String id) throws IOException, IncompleteMessageException, InvalidMessageException, InvalidTokenException, WhatsAppException {
		ProtocolNode serverNode = new ProtocolNode("server", null, null, null);
		List<ProtocolNode> list = new LinkedList<ProtocolNode>();
		list.add(serverNode);
		Map<String,String> xHash = new LinkedHashMap<String, String>();
		xHash.put("xmlns", "jabber:x:event");
		ProtocolNode xNode = new ProtocolNode("x", xHash, list, null);
		Map<String,String> notify = new LinkedHashMap<String, String>();
		notify.put("xmlns","urn:xmpp:whatsapp");
		notify.put("name",name);
		ProtocolNode notnode = new ProtocolNode("notify", notify, null, null);
		Map<String,String> request = new LinkedHashMap<String, String>();
		request.put("xmlns","urn:xmpp:receipts");
		ProtocolNode reqnode = new ProtocolNode("request", request, null, null);

		Map<String,String> messageHash = new LinkedHashMap<String, String>();
		messageHash.put("to",getJID(to));
		messageHash.put("type","chat");
		messageHash.put("id",(id == null?createMsgId("message"):id));
		messageHash.put("t",time());

		list = new LinkedList<ProtocolNode>();
		list.add(xNode);
		list.add(notnode);
		list.add(reqnode);
		list.add(node);
		ProtocolNode messageNode = new ProtocolNode("message", messageHash, list, null);
		if (lastId == null) {
			lastId = messageHash.get("id");
			sendNode(messageNode);
			//listen for response
			waitForServer(messageHash.get("id"));
		} else {
			outQueue.add(messageNode);
		}
		eventManager().fireSendMessage(
				phoneNumber,
				getJID(to),
				messageHash.get("id"),
				node
				);
		return messageHash.get("id");
	}

	private void waitForServer(String id) throws IncompleteMessageException, InvalidMessageException, InvalidTokenException, IOException, WhatsAppException {
		Date start = new Date();
		Date now = start;
		serverReceivedId = null;
		do {
			pollMessages();
			now = new Date();
		} while (!checkReceivedId(id) && (now.getTime() - start.getTime()) < 5000);
	}

	private boolean checkReceivedId(String id) {
		if(log.isDebugEnabled()) {
			log.debug("Checking received id ("+serverReceivedId+" against "+id);
		}
		if(serverReceivedId != null && serverReceivedId.equals(id))
			return true;
		return false;
	}

	private void pollMessages() throws IncompleteMessageException, InvalidMessageException, InvalidTokenException, IOException, WhatsAppException {
		processInboundData(readData());
	}

	private String createMsgId(String prefix) {
		String msgid = prefix + "-" + time() + "-" + ++messageCounter;

		return msgid;
	}

	private String getJID(String number) {
		if (!number.contains("@")) {
			//check if group message
			if (number.contains("-")) {
				//to group
				number = number + "@" + WHATSAPP_GROUP_SERVER;
			} else {
				//to normal user
				number = number + "@" + WHATSAPP_SERVER;
			}
		}

		return number;	
	}

	/**
	 * Parse the message text for emojis
	 *
	 * This will look for special strings in the message text
	 * that need to be replaced with a unicode character to show
	 * the corresponding emoji.
	 *
	 * Emojis should be entered in the message text either as the
	 * correct unicode character directly, or if this isn't possible,
	 * by putting a placeholder of ##unicodeNumber## in the message text.
	 * Include the surrounding ##
	 * eg:
	 * ##1f604## this will show the smiling face
	 * ##1f1ec_1f1e7## this will show the UK flag.
	 *
	 * Notice that if 2 unicode characters are required they should be joined
	 * with an underscore.
	 *
	 *
	 * @param string txt
	 * The message to be parsed for emoji code.
	 *
	 * @return string
	 */
	private String parseMessageForEmojis(String txt) {
		// TODO Auto-generated method stub
		return txt;
	}

	public String getIdentity() {
		return identity;
	}

	public void setIdentity(String identity) {
		this.identity = identity;
	}

	public String getName() {
		return name;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

}
