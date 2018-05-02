import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.infopae.model.BuyAnalyticsData;
import com.infopae.model.SendActuatorData;
import com.infopae.model.SendAnalyticsData;
import com.infopae.model.SendSensorData;

import lac.cnclib.net.NodeConnection;
import lac.cnclib.net.NodeConnectionListener;
import lac.cnclib.net.mrudp.MrUdpNodeConnection;
import lac.cnclib.sddl.message.ApplicationMessage;
import lac.cnclib.sddl.message.Message;
 
public class AnalyticsCoreClient implements NodeConnectionListener {
 
  private static String		gatewayIP   = "192.168.25.34";
  private static int		gatewayPort = 5500;
  private MrUdpNodeConnection	connection;
  
  /** Types of analytics services */
	private final static int GRAPH = 0;
	private final static int ALERT = 1;
	private final static int CUSTOM1 = 2;
  
  /** Active Sensor Data */
	private ConcurrentHashMap<String, ConcurrentHashMap<String, ArrayList<Double[]>>> mActiveRequest = new ConcurrentHashMap<>();
								//macAddress				//uuidData		//uuidClient

	/** Active Analytics User */
	private ConcurrentHashMap<String, ConcurrentHashMap<String, ArrayList<BuyAnalyticsData>>> mActiveRequestOption = new ConcurrentHashMap<>();
								//macAddress				//uuidData		//Option selected
  
  /** The UUID for this device */
  private UUID uuid;
 
  public static void main(String[] args) {
      Logger.getLogger("").setLevel(Level.OFF);
      new AnalyticsCoreClient();
  }
 
  public AnalyticsCoreClient() {
      boolean read = false;
	  
      try {
			File file = new File("uuid.txt");
			FileReader fileReader = new FileReader(file);
			StringBuffer stringBuffer = new StringBuffer();
			int numCharsRead;
			char[] charArray = new char[1024];
			while ((numCharsRead = fileReader.read(charArray)) > 0) {
				stringBuffer.append(charArray, 0, numCharsRead);
				read = true;
			}
			fileReader.close();
			System.out.println(stringBuffer.toString());
			uuid = UUID.fromString(stringBuffer.toString());
		} catch (IOException e) {
		
		}
      
      if(!read) {
    	  try {
			uuid = generateUuid();
			File file = new File("uuid.txt");
			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(uuid.toString());
			fileWriter.flush();
			fileWriter.close();
		} catch (IOException e2) {
		}
      }
      
      InetSocketAddress address = new InetSocketAddress(gatewayIP, gatewayPort);
      try {
          connection = new MrUdpNodeConnection();
          connection.addNodeConnectionListener(this);
          connection.connect(address);
      } catch (IOException e) {
          e.printStackTrace();
      }
	  
  }
 
  @Override
  public void connected(NodeConnection nc) {
	  sendACKMessage(nc);
  }
 
  @Override
  public void newMessageReceived(NodeConnection remoteCon, Message m) {
	  JsonParser parser = new JsonParser();
	  Gson gson = new Gson();

	  if( m.getContentObject() instanceof String ) {
		  String content = new String( m.getContent() );
		  try {
			  JsonElement object = parser.parse(content);
			  MatchmakingData matchmakingData = gson.fromJson(object, MatchmakingData.class);
			  onEvent(matchmakingData);
		  }catch (Exception ex){
		  }
	  }else if(m.getContentObject() instanceof SendSensorData) {
		  SendSensorData sendSensorData = (SendSensorData)m.getContentObject();
	
	  }else if(m.getContentObject() instanceof SendAnalyticsData) {
		  SendAnalyticsData sendAnalyticsData = (SendAnalyticsData) m.getContentObject();
		  onEventMainThread(sendAnalyticsData);
	
	  }else if(m.getContentObject() instanceof BuyAnalyticsData) {
		  BuyAnalyticsData buyAnalyticsData = (BuyAnalyticsData) m.getContentObject();
		  onEventMainThread(buyAnalyticsData);
	
	  }
  }
 
  // other methods
 
  @Override
  public void reconnected(NodeConnection remoteCon, SocketAddress endPoint, boolean wasHandover, boolean wasMandatory) {}
 
  @Override
  public void disconnected(NodeConnection remoteCon) {}
 
  @Override
  public void unsentMessages(NodeConnection remoteCon, List<Message> unsentMessages) {}
 
  @Override
  public void internalException(NodeConnection remoteCon, Exception e) {}
  
  /**
	 * Calculates and sends the analysed data to IoTrade user
	 * @param macAddress MacAddress of sensor to be observed
	 * @param uuid UUID Data of sensor
	 * @param buyAnalyticsData Information of IoTrade user to be sent data
	 */
	private void calculateOption(BuyAnalyticsData buyAnalyticsData, String uuid, String macAddress, long interval){
		SendSensorData sendSensorData = new SendSensorData();
		ArrayList<String> listUuid = new ArrayList<>();
		ArrayList<Double[]> data = new ArrayList<>(mActiveRequest.get(macAddress).get(uuid));
		String uuidClient = buyAnalyticsData.getUuidIotrade();
		int option = buyAnalyticsData.getOption();

		sendSensorData.setUuidClients(listUuid);
		sendSensorData.setInterval(interval);

		listUuid.add(uuidClient);

		if(uuid.equals("f000aa01-0451-4000-b000-000000000000")){
			//"Temperatura"
			if(option == GRAPH){
				sendSensorData.setListData(data);
			}else if(option == CUSTOM1){
				Statistics statisticsAmbient, statisticsTarget;
				double[] ambient = new double[data.size()];
				double[] target = new double[data.size()];
				Double[] ambientSend = new Double[2];
				Double[] targetSend = new Double[2];
				ArrayList<Double[]> sendData = new ArrayList<>();

				for(int i=0;i<data.size();i++){
					ambient[i] = data.get(i)[0];
					target[i] = data.get(i)[1];
				}

				statisticsAmbient = new Statistics(ambient);
				statisticsTarget = new Statistics(target);

				ambientSend[0] = statisticsAmbient.getMean();
				targetSend[0] = statisticsTarget.getMean();

				ambientSend[1] = statisticsAmbient.getStdDev();
				targetSend[1] = statisticsTarget.getStdDev();

				sendData.add(ambientSend);
				sendData.add(targetSend);

				sendSensorData.setListData(sendData);
			}
		} else if(uuid.equals("f000aa11-0451-4000-b000-000000000000")){
			//"Acelerômetro"
			if(option == GRAPH){
				sendSensorData.setListData(data);
			}
		} else if(uuid.equals("f000aa21-0451-4000-b000-000000000000")){
			//"Humidade"
			if(option == GRAPH){
				sendSensorData.setListData(data);
			}else if(option == CUSTOM1){
				Statistics statistics;
				double[] humidity = new double[data.size()];
				Double[] humiditySend = new Double[1];

				for(int i=0;i<data.size();i++){
					humidity[i] = data.get(i)[0];
				}

				statistics = new Statistics(humidity);

				humiditySend[0] = statistics.getMean();

				sendSensorData.setData(humiditySend);
			}
		} else if(uuid.equals("f000aa31-0451-4000-b000-000000000000")){
			//"Magnetômetro"
			if(option == GRAPH){
				sendSensorData.setListData(data);
			}
		} else if(uuid.equals("f000aa41-0451-4000-b000-000000000000")){
			//"Barômetro"
			if(option == GRAPH){
				sendSensorData.setListData(data);
			}else if(option == CUSTOM1){
				Statistics statistics;
				double[] barometer = new double[data.size()];
				Double[] barometerSend = new Double[1];

				for(int i=0;i<data.size();i++){
					barometer[i] = data.get(i)[0];
				}

				statistics = new Statistics(barometer);

				barometerSend[0] = statistics.getMean();

				sendSensorData.setData(barometerSend);
			}
		} else if(uuid.equals("f000aa51-0451-4000-b000-000000000000")){
			//"Giroscópio"
			if(option == GRAPH){
				sendSensorData.setListData(data);
			}
		} else if(uuid.equals("f000aa71-0451-4000-b000-000000000000")){
			//"Luxímetro"
			if(option == GRAPH){
				sendSensorData.setListData(data);
			}
		} else if(uuid.equals("f000aa81-0451-4000-b000-000000000000")){
			//"Movimento"
			if(option == GRAPH){
				sendSensorData.setListData(data);
			}
		}

		if(option != ALERT)
			createAndSendMsg(sendSensorData, this.uuid);
	}
	
	//receives event from connection listner to removes a sensor data and IoTrade user
	public void onEvent( MatchmakingData matchmakingData ) {
		String uuidMatch = matchmakingData.getUuidMatch();
		String macAddress = matchmakingData.getMacAddress();
		String uuidData = matchmakingData.getUuidData();
		String uuidClient = matchmakingData.getUuidClient();
		String uuidClientAnalytics = matchmakingData.getUuidAnalyticsClient();
		boolean ack = matchmakingData.isAck();

		if(ack)
            createAndSendMsg( "a" + uuidClientAnalytics, uuid);

		if(matchmakingData.getStartStop() == MatchmakingData.STOP) {
			ConcurrentHashMap<String, ArrayList<BuyAnalyticsData>> map = mActiveRequestOption.get(macAddress);
			if(map != null) {
				ArrayList<BuyAnalyticsData> arrayList = map.get(uuidData);
				int position = getUuidIoTrade(arrayList, uuidClientAnalytics);

				if (position >= 0) {
					BuyAnalyticsData buyAnalyticsData = arrayList.get(position);

					arrayList.remove(position);
					map.put(uuidData, arrayList);

					if (arrayList.size() == 0) {
						ConcurrentHashMap<String, ArrayList<Double[]>> mapInfo = mActiveRequest.get(macAddress);
						if (mapInfo.containsKey(uuidData))
							mapInfo.remove(uuidData);
					}
				}
			}
		}
	}
	
	//receives event from connection listner and stores sensor data
	public void onEventMainThread( SendAnalyticsData sendAnalyticsData ) {
		Double[] data = sendAnalyticsData.getData();
		String macAddress = sendAnalyticsData.getMacAddress();
		String uuidData = sendAnalyticsData.getUuid();
		long interval = sendAnalyticsData.getInterval();

		if (!mActiveRequest.containsKey(macAddress)) {
			ConcurrentHashMap<String, ArrayList<Double[]>> map = new ConcurrentHashMap<>();
			ArrayList<Double[]> arrayList = new ArrayList<>();
			arrayList.add(data);
			map.put(uuidData, arrayList);
			mActiveRequest.put(macAddress, map);
		} else {
			ConcurrentHashMap<String, ArrayList<Double[]>> map = mActiveRequest.get(macAddress);
			ArrayList<Double[]> arrayList = new ArrayList<>();
			if (map.containsKey(uuidData)) {
				arrayList = map.get(uuidData);
				arrayList.add(data);
			} else {
				arrayList.add(data);
				map.put(uuidData, arrayList);
			}
			mActiveRequest.put(macAddress, map);
		}

		if(mActiveRequestOption.containsKey(macAddress)){
			ConcurrentHashMap<String, ArrayList<BuyAnalyticsData>> map = mActiveRequestOption.get(macAddress);
			if(map.containsKey(uuidData)){
				ArrayList<BuyAnalyticsData> list = map.get(uuidData);
				ArrayList<String> listUuid = new ArrayList<>();
				for(int i=0;i<list.size();i++){
					BuyAnalyticsData buyAnalyticsData = list.get(i);
					calculateOption(buyAnalyticsData, uuidData, macAddress, interval);
				}
			}
		}
	}

	//receives event from connection listner and register an user purchase
	public void onEventMainThread( BuyAnalyticsData buyAnalyticsData ) {
		String macAddress = buyAnalyticsData.getMacAddress();
		String uuidData = buyAnalyticsData.getUuidData();

		if (!mActiveRequestOption.containsKey(macAddress)) {
			ConcurrentHashMap<String, ArrayList<BuyAnalyticsData>> map = new ConcurrentHashMap<>();
			ArrayList<BuyAnalyticsData> arrayList = new ArrayList<>();
			arrayList.add(buyAnalyticsData);
			map.put(uuidData, arrayList);
			mActiveRequestOption.put(macAddress, map);
		} else {
			ConcurrentHashMap<String, ArrayList<BuyAnalyticsData>> map = mActiveRequestOption.get(macAddress);
			ArrayList<BuyAnalyticsData> arrayList = new ArrayList<>();
			if (map.containsKey(uuidData)) {
				arrayList = map.get(uuidData);
				arrayList.add(buyAnalyticsData);
			} else {
				arrayList.add(buyAnalyticsData);
				map.put(uuidData, arrayList);
			}
			mActiveRequestOption.put(macAddress, map);
		}
	}
	
	/**
	 * @param arrayList List of IoTrade Users.
	 * @param uuidClientAnalytics IoTrade User UUID to be found on the list
	 * @return Returns position of IoTrade user in the list
	 */
	private int getUuidIoTrade(ArrayList<BuyAnalyticsData> arrayList, String uuidClientAnalytics){
		for(int i=0;i<arrayList.size();i++){
			BuyAnalyticsData buyAnalyticsData = arrayList.get(i);
			if(buyAnalyticsData.getUuidIotrade().equals(uuidClientAnalytics)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Creates an application message to send to the cloud
	 * It will send the message immediately
	 * @param s The Mobile Hub Message structure
	 * @param sender The UUID of the Mobile Hub
	 */
	private void createAndSendMsg(Serializable s, UUID sender) {

		try {
			ApplicationMessage am = new ApplicationMessage();
			am.setContentObject( s );
			am.setTagList( new ArrayList<String>() );
			am.setSenderID( sender );

			connection.sendMessage( am );
		} catch (Exception e) {
			
		}
	}
  
  /**
   * Sends an ACK message to the cloud and the local services
   * to let them know that the connection (connection or reconnection)
   * is established
   * @param nc The connection
   */
  private void sendACKMessage( NodeConnection nc ) {
      // Send a message once we are reconnected
      ApplicationMessage am = new ApplicationMessage();
      am.setContentObject("ack");
      am.setTagList( new ArrayList<String>() );
      am.setSenderID( uuid );

      try {
          nc.sendMessage( am );
      } catch(IOException e) {
          e.printStackTrace();
      }
  }
  
  /**
	 * It generates a random UUID for the Android device.
	 * 
	 * @return It returns the generated UUID.
	 */
	private static UUID generateUuid() {
		return UUID.randomUUID();
	}
}
