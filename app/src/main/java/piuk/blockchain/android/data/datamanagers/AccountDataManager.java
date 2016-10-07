package piuk.blockchain.android.data.datamanagers;

import android.support.annotation.Nullable;

import info.blockchain.wallet.exceptions.DecryptionException;
import info.blockchain.wallet.exceptions.PayloadException;
import info.blockchain.wallet.multiaddr.MultiAddrFactory;
import info.blockchain.wallet.payload.Account;
import info.blockchain.wallet.payload.LegacyAddress;
import info.blockchain.wallet.payload.Payload;
import info.blockchain.wallet.payload.PayloadManager;
import info.blockchain.wallet.util.CharSequenceX;
import info.blockchain.wallet.util.DoubleEncryptionFactory;

import org.bitcoinj.core.Base58;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;

import java.util.List;

import piuk.blockchain.android.data.rxjava.RxUtil;
import piuk.blockchain.android.data.services.AddressInfoService;
import rx.Observable;
import rx.exceptions.Exceptions;

import static piuk.blockchain.android.data.services.AddressInfoService.PARAMETER_FINAL_BALANCE;

public class AccountDataManager {

    private PayloadManager payloadManager;
    private MultiAddrFactory multiAddrFactory;
    private AddressInfoService addressInfoService;

    public AccountDataManager(PayloadManager payload, MultiAddrFactory addrFactory, AddressInfoService addressService) {
        payloadManager = payload;
        multiAddrFactory = addrFactory;
        addressInfoService = addressService;
    }

    /**
     * Derives new {@link Account} from the master seed
     *
     * @param accountLabel   A label for the account
     * @param secondPassword An optional double encryption password
     * @return An {@link Observable<Account>} wrapping the newly created Account
     */
    public Observable<Account> createNewAccount(String accountLabel, @Nullable CharSequenceX secondPassword) {
        return createNewAccountObservable(accountLabel, secondPassword)
                .compose(RxUtil.applySchedulers());
    }

    /**
     * Sets a private key for an associated {@link LegacyAddress} which is already in the {@link
     * Payload} as a watch only address
     *
     * @param key            An {@link ECKey}
     * @param secondPassword An optional double encryption password
     * @return An {@link Observable<Boolean>} representing a successful save
     */
    public Observable<Boolean> setPrivateKey(ECKey key, @Nullable CharSequenceX secondPassword) {
        Payload payload = payloadManager.getPayload();
        int index = payload.getLegacyAddressStrings().indexOf(key.toAddress(MainNetParams.get()).toString());
        LegacyAddress legacyAddress = payload.getLegacyAddresses().get(index);
        setKeyForLegacyAddress(legacyAddress, key, secondPassword);
        legacyAddress.setWatchOnly(false);
        payloadManager.setPayload(payload);
        return savePayloadToServer();
    }

    /**
     * Sets a private key for a {@link LegacyAddress}
     *
     * @param legacyAddress  The {@link LegacyAddress} to which you wish to add the key
     * @param key            The {@link ECKey} for the address
     * @param secondPassword An optional double encryption password
     */
    public void setKeyForLegacyAddress(LegacyAddress legacyAddress, ECKey key, @Nullable CharSequenceX secondPassword) {
        // If double encrypted, save encrypted in payload
        if (!payloadManager.getPayload().isDoubleEncrypted()) {
            legacyAddress.setEncryptedKey(key.getPrivKeyBytes());
        } else {
            String encryptedKey = Base58.encode(key.getPrivKeyBytes());
            String encrypted2 = DoubleEncryptionFactory.getInstance().encrypt(encryptedKey,
                    payloadManager.getPayload().getSharedKey(),
                    secondPassword != null ? secondPassword.toString() : null,
                    payloadManager.getPayload().getOptions().getIterations());
            legacyAddress.setEncryptedKey(encrypted2);
        }
    }

    /**
     * Allows you to propagate changes to a {@link LegacyAddress} through the {@link Payload} and
     * the {@link MultiAddrFactory}
     *
     * @param legacyAddress The updated address
     * @return {@link Observable<Boolean>} representing a successful save
     */
    public Observable<Boolean> updateLegacyAddress(LegacyAddress legacyAddress) {
        return createUpdateLegacyAddressObservable(legacyAddress)
                .compose(RxUtil.applySchedulers());
    }

    private Observable<Boolean> createUpdateLegacyAddressObservable(LegacyAddress address) {
        return Observable.fromCallable(() -> payloadManager.addLegacyAddress(address))
                .flatMap(RxUtil.ternary(
                        Boolean::booleanValue,
                        aBoolean -> addAddressAndUpdate(address).flatMap(total -> Observable.just(true)),
                        aBoolean -> Observable.just(false)));
    }

    private Observable<Boolean> savePayloadToServer() {
        return Observable.fromCallable(() -> payloadManager.savePayloadToServer())
                .compose(RxUtil.applySchedulers());
    }

    private Observable<Long> addAddressAndUpdate(LegacyAddress address) {
        try {
            List<String> legacyAddressList = payloadManager.getPayload().getLegacyAddressStrings();
            multiAddrFactory.refreshLegacyAddressData(legacyAddressList.toArray(new String[legacyAddressList.size()]), false);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        return addressInfoService.getAddressBalance(address, PARAMETER_FINAL_BALANCE)
                .doOnNext(balance -> {
                    multiAddrFactory.setLegacyBalance(address.getAddress(), balance);
                    multiAddrFactory.setLegacyBalance(MultiAddrFactory.getInstance().getLegacyBalance() + balance);
                });
    }

    private Observable<Account> createNewAccountObservable(String accountLabel, @Nullable CharSequenceX secondPassword) {
        return Observable.create(subscriber -> {
            try {
                payloadManager.addAccount(
                        accountLabel,
                        secondPassword != null ? secondPassword.toString() : null,
                        new PayloadManager.AccountAddListener() {
                            @Override
                            public void onAccountAddSuccess(Account account) {
                                subscriber.onNext(account);
                                subscriber.onCompleted();
                            }

                            @Override
                            public void onSecondPasswordFail() {
                                subscriber.onError(new DecryptionException());
                            }

                            @Override
                            public void onPayloadSaveFail() {
                                subscriber.onError(new PayloadException());
                            }
                        });
            } catch (Exception e) {
                subscriber.onError(e);
            }
        });
    }
}
