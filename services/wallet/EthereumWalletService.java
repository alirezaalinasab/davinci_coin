package ethereum.wallet.service;

import static ethereum.wallet.service.utils.Utils.*;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.picocontainer.Startable;

import ethereum.wallet.model.*;
import commons.api.notification.NotificationContext;
import commons.api.notification.model.NotificationInfo;
import commons.api.notification.model.PluginKey;
import commons.api.notification.service.storage.WebNotificationStorage;
import commons.api.settings.SettingService;
import commons.api.settings.SettingValue;
import commons.api.settings.data.Context;
import commons.api.settings.data.Scope;
import commons.notification.impl.NotificationContextImpl;
import commons.utils.IOUtil;
import commons.utils.ListAccess;
import container.configuration.ConfigurationManager;
import container.xml.InitParams;
import portal.config.UserACL;
import services.cache.CacheService;
import services.listener.ListenerService;
import services.log.Log;
import social.core.identity.model.Identity;
import social.core.identity.provider.OrganizationIdentityProvider;
import social.core.manager.IdentityManager;
import social.core.space.model.Space;
import social.core.space.spi.SpaceService;

/**
 * A storage service to save/load information used by users and spaces wallets
 */
public class EthereumWalletService implements Startable {
	
  private SettingService                       settingService;

  private IdentityManager                      identityManager;

  private SpaceService                         spaceService;

  private UserACL                              userACL;

  private WebNotificationStorage               webNotificationStorage;

  private ListenerService                      listenerService;

  private ConfigurationManager                 configurationManager;

  private GlobalSettings                       defaultSettings               = new GlobalSettings();

  private GlobalSettings                       storedSettings;

  private String                               contractAbiPath;

  private JSONArray                            contractAbi;

  private String                               contractBinaryPath;

  private String                               contractBinary;

	  
  
  private static final Log                     LOG                           = getLogger(EthereumWalletService.class);

  public static final String                   DEFAULT_NETWORK_ID            = "defaultNetworkId";

  public static final String                   DEFAULT_NETWORK_URL           = "defaultNetworkURL";

  public static final String                   DEFAULT_NETWORK_WS_URL        = "defaultNetworkWSURL";

  public static final String                   DEFAULT_ACCESS_PERMISSION     = "defaultAccessPermission";

  public static final String                   DEFAULT_GAS                   = "defaultGas";

  public static final String                   DEFAULT_BLOCKS_TO_RETRIEVE    = "defaultBlocksToRetrieve";

  public static final String                   DEFAULT_CONTRACTS_ADDRESSES   = "defaultContractAddresses";

  public static final String                   SCOPE_NAME                    = "ADDONS_ETHEREUM_WALLET";

  public static final String                   GLOBAL_SETTINGS_KEY_NAME      = "GLOBAL_SETTINGS";

  public static final String                   ADDRESS_KEY_NAME              = "ADDONS_ETHEREUM_WALLET_ADDRESS";

  public static final String                   LAST_BLOCK_NUMBER_KEY_NAME    = "ADDONS_ETHEREUM_LAST_BLOCK_NUMBER";

  public static final String                   SETTINGS_KEY_NAME             = "ADDONS_ETHEREUM_WALLET_SETTINGS";

  public static final Context                  WALLET_CONTEXT                = Context.GLOBAL;

  public static final Scope                    WALLET_SCOPE                  = Scope.APPLICATION.id(SCOPE_NAME);

  public static final String                   WALLET_DEFAULT_CONTRACTS_NAME = "WALLET_DEFAULT_CONTRACTS";

  public static final String                   WALLET_USER_TRANSACTION_NAME  = "WALLET_USER_TRANSACTION";

  public static final String                   WALLET_BROWSER_PHRASE_NAME    = "WALLET_BROWSER_PHRASE";

  public static final String                   ABI_PATH_PARAMETER            = "contract.abi.path";

  public static final String                   BIN_PATH_PARAMETER            = "contract.bin.path";


  
  private static final char[]                  SIMPLE_CHARS                  = new char[] { 'A', 'B', 'C', 'D', 'E', 'F', 'G',
	      'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
	      'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4',
	      '5', '6', '7', '8', '9' };
  public EthereumWalletService(SettingService settingService,
                               SpaceService spaceService,
                               WebNotificationStorage webNotificationStorage,
                               IdentityManager identityManager,
                               ListenerService listenerService,
                               UserACL userACL,
                               CacheService cacheService,
                               ConfigurationManager configurationManager,
                               InitParams params) {
    this.configurationManager = configurationManager;
    this.settingService = settingService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.webNotificationStorage = webNotificationStorage;
    this.listenerService = listenerService;
    this.userACL = userACL;
    this.transactionMessagesCache = cacheService.getCacheInstance("wallet.transactionsMessages");

    if (params.containsKey(DEFAULT_NETWORK_ID)) {
      String value = params.getValueParam(DEFAULT_NETWORK_ID).getValue();
      long defaultNetworkId = Long.parseLong(value);
      defaultSettings.setDefaultNetworkId(defaultNetworkId);
    }

    if (params.containsKey(DEFAULT_NETWORK_URL)) {
      String defaultNetworkURL = params.getValueParam(DEFAULT_NETWORK_URL).getValue();
      defaultSettings.setProviderURL(defaultNetworkURL);
    }

    if (params.containsKey(DEFAULT_ACCESS_PERMISSION)) {
      String defaultAccessPermission = params.getValueParam(DEFAULT_ACCESS_PERMISSION).getValue();
      defaultSettings.setAccessPermission(defaultAccessPermission);
    }

    if (params.containsKey(DEFAULT_GAS)) {
      String value = params.getValueParam(DEFAULT_GAS).getValue();
      int defaultGas = Integer.parseInt(value);
      defaultSettings.setDefaultGas(defaultGas);
    }

    if (params.containsKey(DEFAULT_BLOCKS_TO_RETRIEVE)) {
      String value = params.getValueParam(DEFAULT_BLOCKS_TO_RETRIEVE).getValue();
      int defaultBlocksToRetrieve = Integer.parseInt(value);
      defaultSettings.setDefaultBlocksToRetrieve(defaultBlocksToRetrieve);
    }

    if (params.containsKey(DEFAULT_CONTRACTS_ADDRESSES)) {
      String defaultContractsToDisplay = params.getValueParam(DEFAULT_CONTRACTS_ADDRESSES).getValue();
      if (StringUtils.isNotBlank(defaultContractsToDisplay)) {
        List<String> defaultContracts = Arrays.stream(defaultContractsToDisplay.split(","))
                                              .map(contractAddress -> contractAddress.trim().toLowerCase())
                                              .filter(contractAddress -> !contractAddress.isEmpty())
                                              .collect(Collectors.toList());
        defaultSettings.setDefaultContractsToDisplay(defaultContracts);
      }
    }

    if (params.containsKey(ABI_PATH_PARAMETER)) {
      contractAbiPath = params.getValueParam(ABI_PATH_PARAMETER).getValue();
    }
    if (StringUtils.isBlank(contractAbiPath)) {
      LOG.warn("Contract ABI path is empty, thus no contract deployment is possible");
    }
    if (params.containsKey(BIN_PATH_PARAMETER)) {
      contractBinaryPath = params.getValueParam(BIN_PATH_PARAMETER).getValue();
    }
    if (StringUtils.isBlank(contractBinaryPath)) {
      LOG.warn("Contract BIN path is empty, thus no contract deployment is possible");
    }
  }

  @Override
  public void start() {
    try {
      String contractAbiString = IOUtil.getStreamContentAsString(this.configurationManager.getInputStream(contractAbiPath));
      contractAbi = new JSONArray(contractAbiString);
      contractBinary = IOUtil.getStreamContentAsString(this.configurationManager.getInputStream(contractBinaryPath));
      if (!contractBinary.startsWith("0x")) {
        contractBinary = "0x" + contractBinary;
      }
    } catch (Exception e) {
      LOG.warn("Can't read ABI/BIN files content", e);
    }
  }

  @Override
  public void stop() {
  }

  /**
   * Get Contract ABI
   * 
   * @return
   */
  public JSONArray getContractAbi() {
    return contractAbi;
  }

  /**
   * Get Contract BINARY to deploy
   * 
   * @return
   */
  public String getContractBinary() {
    return contractBinary;
  }

  /**
   * Save global settings
   * 
   * @param newGlobalSettings
   */
  public void saveSettings(GlobalSettings newGlobalSettings) {
    if (newGlobalSettings == null) {
      throw new IllegalArgumentException("globalSettings parameter is mandatory");
    }

    GlobalSettings oldGlobalSettings = getSettings();

    LOG.debug("Saving new global settings", newGlobalSettings.toJSONString(false));

    settingService.set(WALLET_CONTEXT,
                       WALLET_SCOPE,
                       GLOBAL_SETTINGS_KEY_NAME,
                       SettingValue.create(newGlobalSettings.toJSONString(false)));

    // Clear cached in memory stored settings
    this.storedSettings = null;

    try {
      this.listenerService.broadcast(GLOAL_SETTINGS_CHANGED_EVENT, oldGlobalSettings, newGlobalSettings);
    } catch (Exception e) {
      LOG.error("An error occurred while broadcasting wallet settings modification event", e);
    }
  }

  /**
   * Retrieves global stored settings used for all users.
   * 
   * @return
   */
  public GlobalSettings getSettings() {
    if (storedSettings != null) {
      // Retrieve stored global settings from memory
      return storedSettings;
    }
    return storedSettings = getSettings(null);
  }

  /**
   * Retrieves global stored settings. if username is not null, the personal
   * settings will be included.
   * 
   * @param networkId
   * @return
   */
  public GlobalSettings getSettings(Long networkId) {
    return getSettings(networkId, null);
  }

  /**
   * Retrieves global stored settings. if username is not null, the personal
   * settings will be included. if spaceId is not null wallet address will be
   * retrieved
   * 
   * @param networkId
   * @param spaceId
   * @return
   */
  public GlobalSettings getSettings(Long networkId, String spaceId) {
    SettingValue<?> globalSettingsValue = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, GLOBAL_SETTINGS_KEY_NAME);

    String username = getCurrentUserId();

    GlobalSettings globalSettings = defaultSettings;
    if (globalSettingsValue != null && globalSettingsValue.getValue() != null) {
      globalSettings = GlobalSettings.parseStringToObject(defaultSettings, globalSettingsValue.getValue().toString());
      if (StringUtils.isNotBlank(globalSettings.getAccessPermission())) {
        Space space = spaceService.getSpaceByPrettyName(globalSettings.getAccessPermission());
        if (space == null) {
          space = spaceService.getSpaceByUrl(globalSettings.getAccessPermission());
          if (space == null) {
            space = spaceService.getSpaceByGroupId("/spaces/" + globalSettings.getAccessPermission());
          }
        }
        // Disable wallet for users not member of the permitted space members
        if (username != null && space != null
            && !(spaceService.isMember(space, username) || spaceService.isSuperManager(username))) {

          LOG.info("Wallet is disabled for user {} because he's not member of space {}", username, space.getPrettyName());

          globalSettings.setWalletEnabled(false);
        }
      }
      globalSettings.setAdmin(userACL.isUserInGroup(ADMINISTRATORS_GROUP));
    }

    if (globalSettings.isWalletEnabled() || globalSettings.isAdmin()) {
      if ((networkId == null || networkId == 0) && globalSettings.getDefaultNetworkId() != null) {
        networkId = globalSettings.getDefaultNetworkId();
      }
      // Retrieve default contracts to display for all users
      globalSettings.setDefaultContractsToDisplay(getDefaultContractsAddresses(networkId));

      if (username != null) {
        // Append user preferences
        SettingValue<?> userSettingsValue = settingService.get(Context.USER.id(username), WALLET_SCOPE, SETTINGS_KEY_NAME);
        UserPreferences userSettings = null;
        if (userSettingsValue != null && userSettingsValue.getValue() != null) {
          userSettings = UserPreferences.parseStringToObject(userSettingsValue.getValue().toString());
        } else {
          userSettings = new UserPreferences();
        }
        globalSettings.setUserPreferences(userSettings);

        if (StringUtils.isNotBlank(spaceId)) {
          userSettings.setWalletAddress(getSpaceAddress(spaceId));
          userSettings.setPhrase(getSpacePhrase(spaceId));
        } else {
          userSettings.setWalletAddress(getUserAddress(username));
          userSettings.setPhrase(getUserPhrase(username));
        }
      }
      globalSettings.setContractAbi(getContractAbi());
      globalSettings.setContractBin(getContractBinary());
    } else {
      globalSettings = new GlobalSettings();
      globalSettings.setWalletEnabled(false);
    }

    return globalSettings;
  }

  /**
   * Save a new contract address to display it in wallet of all users and save
   * contract name and symbol
   * 
   * @param contractDetail
   */
  public void saveDefaultContract(ContractDetail contractDetail) {
    if (StringUtils.isBlank(contractDetail.getAddress())) {
      throw new IllegalArgumentException("address parameter is mandatory");
    }
    if (contractDetail.getNetworkId() == null || contractDetail.getNetworkId() == 0) {
      throw new IllegalArgumentException("networkId parameter is mandatory");
    }

    String defaultContractsParamKey = WALLET_DEFAULT_CONTRACTS_NAME + contractDetail.getNetworkId();

    String address = contractDetail.getAddress().toLowerCase();

    settingService.set(WALLET_CONTEXT,
                       WALLET_SCOPE,
                       address + contractDetail.getNetworkId(),
                       SettingValue.create(contractDetail.toJSONString()));

    // Save the contract address in the list of default contract addreses
    SettingValue<?> defaultContractsAddressesValue = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, defaultContractsParamKey);
    String defaultContractsAddresses =
                                     defaultContractsAddressesValue == null ? address
                                                                            : defaultContractsAddressesValue.getValue().toString()
                                                                                + "," + address;
    settingService.set(WALLET_CONTEXT, WALLET_SCOPE, defaultContractsParamKey, SettingValue.create(defaultContractsAddresses));

    // Clear cached in memory stored settings
    this.storedSettings = null;
  }

  /**
   * Removes a contract address from default contracts displayed in wallet of
   * all users
   * 
   * @param address
   * @param networkId
   * @return
   */
  public boolean removeDefaultContract(String address, Long networkId) {
    if (StringUtils.isBlank(address)) {
      LOG.warn("Can't remove empty address for contract");
      return false;
    }
    if (networkId == null || networkId == 0) {
      LOG.warn("Can't remove empty network id for contract");
      return false;
    }

    String defaultContractsParamKey = WALLET_DEFAULT_CONTRACTS_NAME + networkId;
    final String defaultAddressToSave = address.toLowerCase();
    SettingValue<?> defaultContractsAddressesValue = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, defaultContractsParamKey);
    if (defaultContractsAddressesValue != null) {
      String[] contractAddresses = defaultContractsAddressesValue.getValue().toString().split(",");
      Set<String> contractAddressList = Arrays.stream(contractAddresses)
                                              .filter(contractAddress -> !contractAddress.equalsIgnoreCase(defaultAddressToSave))
                                              .collect(Collectors.toSet());
      String contractAddressValue = StringUtils.join(contractAddressList, ",");

      settingService.remove(WALLET_CONTEXT, WALLET_SCOPE, address + networkId);
      settingService.set(WALLET_CONTEXT, WALLET_SCOPE, defaultContractsParamKey, SettingValue.create(contractAddressValue));
    }

    // Clear cached in memory stored settings
    this.storedSettings = null;

    return true;
  }

  /**
   * Get default contract detail
   * 
   * @param address
   * @param networkId
   * @return
   */
  public ContractDetail getDefaultContractDetail(String address, Long networkId) {
    if (StringUtils.isBlank(address)) {
      LOG.warn("Can't remove empty address for contract");
      return null;
    }
    if (networkId == null || networkId == 0) {
      LOG.warn("Can't remove empty network id for contract");
      return null;
    }

    SettingValue<?> contractDetailValue = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, address + networkId);
    if (contractDetailValue != null) {
      return ContractDetail.parseStringToObject((String) contractDetailValue.getValue());
    }
    return null;
  }

  /**
   * Retrieves the list of default contract addreses
   * 
   * @param networkId
   * @return
   */
  public List<String> getDefaultContractsAddresses(Long networkId) {
    if (networkId == null || networkId == 0) {
      return Collections.emptyList();
    }
    List<String> contractAddressList = null;
    String defaultContractsParamKey = WALLET_DEFAULT_CONTRACTS_NAME + networkId;
    SettingValue<?> defaultContractsAddressesValue = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, defaultContractsParamKey);
    if (defaultContractsAddressesValue != null) {
      String defaultContractsAddressesString = defaultContractsAddressesValue.getValue().toString().toLowerCase();
      String[] contractAddresses = defaultContractsAddressesString.split(",");
      contractAddressList = Arrays.stream(contractAddresses).map(String::toLowerCase).collect(Collectors.toList());
    } else {
      contractAddressList = Collections.emptyList();
    }
    return contractAddressList;
  }

  /**
   * Save user preferences of Wallet
   * 
   * @param userPreferences
   */
  public void saveUserPreferences(String userId, UserPreferences userPreferences) {
    if (userPreferences == null) {
      throw new IllegalArgumentException("userPreferences parameter is mandatory");
    }
    settingService.set(Context.USER.id(userId),
                       WALLET_SCOPE,
                       SETTINGS_KEY_NAME,
                       SettingValue.create(userPreferences.toJSONString()));

    // Clear cached in memory stored settings
    this.storedSettings = null;
  }

  /**
   * Retrieve Space account details DTO
   * 
   * @param id
   * @return {@link AccountDetail}
   */
  public AccountDetail getSpaceDetails(String id) {
    if (id == null) {
      throw new IllegalArgumentException("id parameter is mandatory");
    }

    Space space = getSpace(id);
    if (space == null) {
      return null;
    }

    String avatarUrl = space.getAvatarUrl();
    if (StringUtils.isBlank(avatarUrl)) {
      avatarUrl = "/rest/v1/social/spaces/" + id + "/avatar";
    }
    return new AccountDetail(id,
                             space.getId(),
                             SPACE_ACCOUNT_TYPE,
                             space.getDisplayName(),
                             null,
                             spaceService.isManager(space, getCurrentUserId()) || spaceService.isSuperManager(getCurrentUserId()),
                             avatarUrl);
  }

  /**
   * Retrieve User account details DTO
   * 
   * @param id
   * @return
   */
  public AccountDetail getUserDetails(String id) {
    if (id == null) {
      throw new IllegalArgumentException("id parameter is mandatory");
    }

    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, id, true);
    if (identity == null || identity.getProfile() == null) {
      return null;
    }

    String avatarUrl = identity.getProfile().getAvatarUrl();
    if (StringUtils.isBlank(avatarUrl)) {
      avatarUrl = "/rest/v1/social/users/" + id + "/avatar";
    }
    return new AccountDetail(id,
                             identity.getId(),
                             USER_ACCOUNT_TYPE,
                             identity.getProfile().getFullName(),
                             null,
                             false,
                             avatarUrl);
  }

  /**
   * Retrieve User or Space account details DTO by wallet address
   * 
   * @param address
   * @return
   */
  public AccountDetail getAccountDetailsByAddress(String address) {
    if (address == null) {
      throw new IllegalArgumentException("address parameter is mandatory");
    }

    address = address.toLowerCase();

    AccountDetail accountDetail = null;

    SettingValue<?> walletAddressValue = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, address);
    if (walletAddressValue != null && walletAddressValue.getValue() != null) {
      String idAndType = walletAddressValue.getValue().toString();
      String id = null;
      if (idAndType.startsWith(USER_ACCOUNT_TYPE)) {
        id = idAndType.replaceFirst(USER_ACCOUNT_TYPE, "");
        accountDetail = getUserDetails(id);
      } else if (idAndType.startsWith(SPACE_ACCOUNT_TYPE)) {
        id = idAndType.replaceFirst(SPACE_ACCOUNT_TYPE, "");
        accountDetail = getSpaceDetails(id);
      }
      if (accountDetail == null) {
        LOG.info("Can't find the user/space with id {} associated to address {}", id, address);
      } else {
        accountDetail.setAddress(address);
      }
    }
    return accountDetail;
  }

  /**
   * Get associated address to a space
   * 
   * @param id
   * @return
   */
  public String getSpaceAddress(String id) {
    SettingValue<?> spaceWalletAddressValue = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, id);
    String address = null;
    if (spaceWalletAddressValue != null && spaceWalletAddressValue.getValue() != null) {
      address = spaceWalletAddressValue.getValue().toString().toLowerCase();
    }
    return address;
  }

  /**
   * Get associated address to a user
   * 
   * @param id
   * @return
   */
  public String getUserAddress(String id) {
    SettingValue<?> userWalletAddressValue = settingService.get(Context.USER.id(id), WALLET_SCOPE, ADDRESS_KEY_NAME);
    String address = null;
    if (userWalletAddressValue != null && userWalletAddressValue.getValue() != null) {
      address = userWalletAddressValue.getValue().toString().toLowerCase();
    }
    return address;
  }

  /**
   * Save wallet address to currentUser or to a space managed to current user,
   * switch details in accountDetail parameter
   * 
   * @param accountDetail
   * @return
   * @throws IllegalAccessException
   */
  public String saveWalletAddress(AccountDetail accountDetail) throws Exception {
    String currentUserId = getCurrentUserId();
    String id = accountDetail.getId();
    String type = accountDetail.getType();
    String address = accountDetail.getAddress();
    address = address.toLowerCase();

    if (StringUtils.isBlank(id) || StringUtils.isBlank(type)
        || !(StringUtils.equals(type, USER_ACCOUNT_TYPE) || StringUtils.equals(type, SPACE_ACCOUNT_TYPE))) {
      LOG.warn("Bad request sent to server with id '{}', type '{}' and address '{}'", id, type, address);
      throw new IllegalStateException();
    }

    String oldAddress = null;

    if (StringUtils.equals(type, USER_ACCOUNT_TYPE)) {
      if (!StringUtils.equals(currentUserId, id)) {
        LOG.error("User '{}' attempts to modify wallet address of user '{}'", currentUserId, id);
        throw new IllegalAccessException();
      }

      oldAddress = getUserAddress(id);
      if (oldAddress != null && !StringUtils.equals(oldAddress, address)) {
        AccountDetail userDetailsByOldAddress = getAccountDetailsByAddress(oldAddress);
        if (userDetailsByOldAddress != null) {
          LOG.info("The address {} was assigned to user {} and changed to user {}",
                   oldAddress,
                   userDetailsByOldAddress.getId(),
                   currentUserId);
          settingService.remove(Context.USER.id(userDetailsByOldAddress.getId()), WALLET_SCOPE, ADDRESS_KEY_NAME);
        }
        // Remove old address mapping
        settingService.remove(WALLET_CONTEXT, WALLET_SCOPE, oldAddress);
      }

      settingService.set(WALLET_CONTEXT, WALLET_SCOPE, address, SettingValue.create(type + id));
      settingService.set(Context.USER.id(id), WALLET_SCOPE, ADDRESS_KEY_NAME, SettingValue.create(address));
    } else if (StringUtils.equals(type, SPACE_ACCOUNT_TYPE)) {
      checkCurrentUserIsSpaceManager(id);
      oldAddress = getSpaceAddress(id);
      if (oldAddress != null && !StringUtils.equals(oldAddress, address)) {
        // Remove old address mapping
        settingService.remove(WALLET_CONTEXT, WALLET_SCOPE, oldAddress);
      }

      settingService.set(WALLET_CONTEXT, WALLET_SCOPE, address, SettingValue.create(type + id));
      settingService.set(WALLET_CONTEXT, WALLET_SCOPE, id, SettingValue.create(address));
    } else {
      return null;
    }

    if (StringUtils.isBlank(oldAddress)) {
      this.listenerService.broadcast(NEW_ADDRESS_ASSOCIATED_EVENT, this, accountDetail);
    } else {
      this.listenerService.broadcast(MODIFY_ADDRESS_ASSOCIATED_EVENT, this, accountDetail);
    }

    return generateSecurityPhrase(accountDetail);
  }

  /**
   * Returns last watched block
   * 
   * @param networkId
   * @return
   */
  public long getLastWatchedBlockNumber(long networkId) {
    SettingValue<?> lastBlockNumberValue =
                                         settingService.get(WALLET_CONTEXT, WALLET_SCOPE, LAST_BLOCK_NUMBER_KEY_NAME + networkId);
    if (lastBlockNumberValue != null && lastBlockNumberValue.getValue() != null) {
      return Long.valueOf(lastBlockNumberValue.getValue().toString());
    }
    return 0;
  }

  /**
   * Save last watched block
   * 
   * @param networkId
   * @param lastWatchedBlockNumber
   */
  public void saveLastWatchedBlockNumber(long networkId, long lastWatchedBlockNumber) {
    LOG.debug("Save watched block number {} on network {}", lastWatchedBlockNumber, networkId);
    settingService.set(WALLET_CONTEXT,
                       WALLET_SCOPE,
                       LAST_BLOCK_NUMBER_KEY_NAME + networkId,
                       SettingValue.create(lastWatchedBlockNumber));
  }

  /**
   * Save transaction hash for an account
   * 
   * @param networkId
   * @param address
   * @param hash
   * @param sender
   */
  public void saveAccountTransaction(Long networkId, String address, String hash, boolean sender) {
    if (StringUtils.isBlank(address)) {
      throw new IllegalArgumentException("address parameter is mandatory");
    }
    if (StringUtils.isBlank(hash)) {
      throw new IllegalArgumentException("transaction hash parameter is mandatory");
    }

    address = address.toLowerCase();
    String addressTransactionsParamName = WALLET_USER_TRANSACTION_NAME + address + networkId;

    SettingValue<?> addressTransactionsValue = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, addressTransactionsParamName);
    String addressTransactions = addressTransactionsValue == null ? "" : addressTransactionsValue.getValue().toString();
    if (!addressTransactions.contains(hash)) {
      String content = hash;
      TransactionMessage transactionMessage = transactionMessagesCache.get(hash);
      if (transactionMessage != null) {
        if (!sender) {
          // Avoid saving label only for sender
          transactionMessage = new TransactionMessage(transactionMessage.getHash(), null, transactionMessage.getMessage(), null);
        }
        content = transactionMessage.toString();
      }
      addressTransactions = addressTransactions.isEmpty() ? content : content + "," + addressTransactions;
      settingService.set(WALLET_CONTEXT, WALLET_SCOPE, addressTransactionsParamName, SettingValue.create(addressTransactions));
    }
  }

  /**
   * Get list of transaction hashes per user
   * 
   * @param networkId
   * @param address
   * @return
   */
  public List<JSONObject> getAccountTransactions(Long networkId, String address) {
    String addressTransactionsParamName = WALLET_USER_TRANSACTION_NAME + address + networkId;
    SettingValue<?> addressTransactionsValue = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, addressTransactionsParamName);
    String addressTransactions = addressTransactionsValue == null ? "" : addressTransactionsValue.getValue().toString();
    String[] addressTransactionsArray = addressTransactions.isEmpty() ? new String[0] : addressTransactions.split(",");
    return Arrays.stream(addressTransactionsArray)
                 .map(transaction -> new TransactionMessage(transaction).toJSONObject())
                 .collect(Collectors.toList());
  }

  /**
   * Request funds
   * 
   * @param fundsRequest
   * @throws IllegalAccessException
   */
  public void requestFunds(FundsRequest fundsRequest) throws IllegalAccessException {
    String currentUser = getCurrentUserId();

    AccountDetail requestSender = getAccountDetailsByAddress(fundsRequest.getAddress());
    if (requestSender == null) {
      throw new IllegalStateException("Bad request sent to server with invalid sender address");
    }

    String requestSenderId = requestSender.getId();
    String requestSenderType = requestSender.getType();

    if (StringUtils.equals(requestSenderType, USER_ACCOUNT_TYPE) && !StringUtils.equals(currentUser, requestSenderId)) {
      LOG.warn("Bad request sent to server with invalid sender address");
      throw new IllegalAccessException("Bad request sent to server with invalid sender address");
    }

    if (StringUtils.equals(requestSenderType, SPACE_ACCOUNT_TYPE)) {
      checkCurrentUserIsSpaceManager(requestSenderId);
    }

    NotificationContext ctx = NotificationContextImpl.cloneInstance();

    GlobalSettings settings = getSettings();
    if (!StringUtils.isBlank(fundsRequest.getContract())) {
      ContractDetail contractDetail = getDefaultContractDetail(fundsRequest.getContract(), settings.getDefaultNetworkId());
      if (contractDetail == null) {
        throw new IllegalStateException("Bad request sent to server with invalid contract address (O ly default addresses are permitted)");
      }
      ctx.append(CONTRACT_DETAILS_PARAMETER, contractDetail);
    }

    String requestReceipientId = fundsRequest.getReceipient();
    String requestReceipientType = fundsRequest.getReceipientType();

    AccountDetail requestReceipient = null;
    if (USER_ACCOUNT_TYPE.equals(requestReceipientType)) {
      requestReceipient = getUserDetails(requestReceipientId);
    } else if (SPACE_ACCOUNT_TYPE.equals(requestReceipientType)) {
      requestReceipient = getSpaceDetails(requestReceipientId);
    }

    if (requestReceipient == null || requestReceipient.getTechnicalId() == null) {
      LOG.warn("Can't find fund request recipient with id {} and type {}", requestReceipientId, requestReceipientType);
    }

    ctx.append(FUNDS_REQUEST_SENDER_DETAIL_PARAMETER, getUserDetails(getCurrentUserId()));
    ctx.append(SENDER_ACCOUNT_DETAIL_PARAMETER, requestSender);
    ctx.append(RECEIVER_ACCOUNT_DETAIL_PARAMETER, requestReceipient);
    ctx.append(FUNDS_REQUEST_PARAMETER, fundsRequest);

    ctx.getNotificationExecutor().with(ctx.makeCommand(PluginKey.key(FUNDS_REQUEST_NOTIFICATION_ID))).execute(ctx);
  }

  /**
   * Mark a fund request web notification as sent
   * 
   * @param notificationId
   * @param currentUser
   * @throws IllegalAccessException if current user is not the targetted user of
   *           notification
   */
  public void markFundRequestAsSent(String notificationId, String currentUser) throws IllegalAccessException {
    NotificationInfo notificationInfo = webNotificationStorage.get(notificationId);
    if (notificationInfo == null) {
      throw new IllegalStateException("Notification with id " + notificationId + " wasn't found");
    }
    if (notificationInfo.getTo() == null || !currentUser.equals(notificationInfo.getTo())) {
      throw new IllegalAccessException("Target user of notification '" + notificationId + "' is different from current user");
    }
    notificationInfo.getOwnerParameter().put(FUNDS_REQUEST_SENT, "true");
    webNotificationStorage.update(notificationInfo, false);
  }

  /**
   * Get fund request status
   * 
   * @param notificationId
   * @param currentUser
   * @return true if fund request sent
   * @throws IllegalAccessException if current user is not the targetted user of
   *           notification
   */
  public boolean isFundRequestSent(String notificationId, String currentUser) throws IllegalAccessException {
    NotificationInfo notificationInfo = webNotificationStorage.get(notificationId);
    if (notificationInfo == null) {
      throw new IllegalStateException("Notification with id " + notificationId + " wasn't found");
    }
    if (notificationInfo.getTo() == null || !currentUser.equals(notificationInfo.getTo())) {
      throw new IllegalAccessException("Target user of notification '" + notificationId + "' is different from current user");
    }
    String fundRequestSentString = notificationInfo.getOwnerParameter().get(FUNDS_REQUEST_SENT);
    return Boolean.parseBoolean(fundRequestSentString);
  }

  /**
   * Retrieves the list registered wallets
   * 
   * @return
   * @throws Exception
   */
  public List<AccountDetail> lisWallets() throws Exception {
    List<AccountDetail> accounts = new ArrayList<>();
    Map<String, String> usernames = getListOfWalletsOfType(USER_ACCOUNT_TYPE);
    for (String username : usernames.keySet()) {
      AccountDetail details = getUserDetails(username);
      if (details != null) {
        details.setAddress(usernames.get(username));
        accounts.add(details);
      }
    }

    Map<String, String> spaces = getListOfWalletsOfType(SPACE_ACCOUNT_TYPE);
    for (String spaceId : spaces.keySet()) {
      AccountDetail details = getSpaceDetails(spaceId);
      if (details != null) {
        details.setAddress(spaces.get(spaceId));
        accounts.add(details);
      }
    }
    return accounts;
  }

  /**
   * Save temporary transaction label and message
   * 
   * @param transactionMessage
   */
  public void saveTransactionMessage(TransactionMessage transactionMessage) {
    this.transactionMessagesCache.put(transactionMessage.getHash(), transactionMessage);
  }

  /**
   * Get temporary transaction label and message
   * 
   * @param transactionHash
   * @return
   */
  public TransactionMessage getTransactionMessage(String transactionHash) {
    return this.transactionMessagesCache.get(transactionHash);
  }

  /**
   * Remove transaction message object
   * 
   * @param hash
   * @return
   */
  public TransactionMessage removeTransactionMessageFromCache(String hash) {
    return this.transactionMessagesCache.remove(hash);
  }

  /**
   * Retreive the ABI content of a contract
   * 
   * @param name
   * @return
   * @throws Exception
   */
  public String getContract(String name, String extension) throws Exception {
    try (InputStream abiInputStream = this.getClass()
                                          .getClassLoader()
                                          .getResourceAsStream("ethereum/wallet/contract/" + name + "."
                                              + extension)) {
      return IOUtils.toString(abiInputStream);
    }
  }

  private Map<String, String> getListOfWalletsOfType(String walletType) throws Exception {
    if (StringUtils.isBlank(walletType) || !(USER_ACCOUNT_TYPE.equals(walletType) || SPACE_ACCOUNT_TYPE.equals(walletType))) {
      throw new IllegalArgumentException("Unrecognized wallet type: " + walletType);
    }
    Map<String, String> names = new HashMap<>();
    if (USER_ACCOUNT_TYPE.equals(walletType)) {
      int pageSize = 100;
      int current = 0;
      List<Context> contexts = null;
      do {
        contexts = settingService.getContextsByTypeAndScopeAndSettingName(Context.USER.getName(),
                                                                          WALLET_SCOPE.getName(),
                                                                          WALLET_SCOPE.getId(),
                                                                          ADDRESS_KEY_NAME,
                                                                          current,
                                                                          pageSize);
        if (contexts != null && !contexts.isEmpty()) {
          List<String> usernames = contexts.stream().map(context -> context.getId()).collect(Collectors.toList());
          for (String username : usernames) {
            names.put(username, getUserAddress(username));
          }
        }
        current += pageSize;
      } while (contexts != null && contexts.size() == pageSize);
    } else {
      int pageSize = 100;
      int current = 0;
      Space[] spaces = null;
      do {
        ListAccess<Space> spacesListAccress = spaceService.getAllSpacesWithListAccess();
        spaces = spacesListAccress.load(current, pageSize);
        if (spaces != null && spaces.length > 0) {
          for (Space space : spaces) {
            String spaceId = getSpaceId(space);
            SettingValue<?> spaceAddress = settingService.get(WALLET_CONTEXT, WALLET_SCOPE, spaceId);
            if (spaceAddress != null && spaceAddress.getValue() != null) {
              names.put(spaceId, spaceAddress.getValue().toString());
            }
          }
        }
        current += pageSize;
      } while (spaces != null && spaces.length == pageSize);
    }
    return names;
  }

  private String getSpaceId(Space space) {
    return space.getGroupId().split("/")[2];
  }

  private String generateSecurityPhrase(AccountDetail accountDetail) throws IllegalAccessException {
    String currentUser = getCurrentUserId();
    String id = accountDetail.getId();
    String type = accountDetail.getType();

    Context context = null;
    String paramName = null;

    if (StringUtils.equals(type, USER_ACCOUNT_TYPE) && StringUtils.equals(currentUser, id)) {
      context = Context.USER.id(id);
      paramName = WALLET_BROWSER_PHRASE_NAME;
    } else if (StringUtils.equals(type, SPACE_ACCOUNT_TYPE)) {
      checkCurrentUserIsSpaceManager(id);
      context = WALLET_CONTEXT;
      paramName = WALLET_BROWSER_PHRASE_NAME + id;
    } else {
      return null;
    }

    SettingValue<?> browserWalletPhraseValue = settingService.get(context, WALLET_SCOPE, paramName);
    if (browserWalletPhraseValue != null && browserWalletPhraseValue.getValue() != null) {
      return browserWalletPhraseValue.getValue().toString();
    }
    String phrase = RandomStringUtils.random(20, SIMPLE_CHARS);
    settingService.set(context, WALLET_SCOPE, paramName, SettingValue.create(phrase));
    return phrase;
  }

  private String getUserPhrase(String username) {
    SettingValue<?> browserWalletPhraseValue = settingService.get(Context.USER.id(username),
                                                                  WALLET_SCOPE,
                                                                  WALLET_BROWSER_PHRASE_NAME);
    if (browserWalletPhraseValue != null && browserWalletPhraseValue.getValue() != null) {
      return browserWalletPhraseValue.getValue().toString();
    }
    return null;
  }

  private String getSpacePhrase(String spaceId) {
    try {
      boolean isSpaceManager = checkCurrentUserIsSpaceManager(spaceId, false);
      if (!isSpaceManager) {
        return null;
      }
    } catch (Exception e) {
      return null;
    }
    SettingValue<?> browserWalletPhraseValue = settingService.get(WALLET_CONTEXT,
                                                                  WALLET_SCOPE,
                                                                  WALLET_BROWSER_PHRASE_NAME + spaceId);
    if (browserWalletPhraseValue != null && browserWalletPhraseValue.getValue() != null) {
      return browserWalletPhraseValue.getValue().toString();
    }
    return null;
  }

  private boolean checkCurrentUserIsSpaceManager(String id) throws IllegalAccessException {
    return checkCurrentUserIsSpaceManager(id, true);
  }

  private boolean checkCurrentUserIsSpaceManager(String id, boolean throwException) throws IllegalAccessException {
    String currentUserId = getCurrentUserId();
    Space space = getSpace(id);
    if (space == null) {
      LOG.warn("Space not found with id '{}'", id);
      throw new IllegalStateException();
    }
    if (!spaceService.isManager(space, currentUserId) && !spaceService.isSuperManager(currentUserId)) {
      if (throwException) {
        LOG.error("User '{}' attempts to modify wallet address of space '{}'", currentUserId, space.getDisplayName());
        throw new IllegalAccessException();
      } else {
        return false;
      }
    }
    return true;
  }

}
