package piuk.blockchain.android.data.datamanagers;

import info.blockchain.api.DynamicFee;
import info.blockchain.api.Unspent;
import info.blockchain.wallet.payment.data.SpendableUnspentOutputs;
import info.blockchain.wallet.payment.data.SuggestedFee;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.BIP38PrivateKey;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.List;

import io.reactivex.Observable;
import piuk.blockchain.android.data.rxjava.RxUtil;
import piuk.blockchain.android.data.services.PaymentService;

public class SendDataManager {

    private PaymentService paymentService;
    private DynamicFee dynamicFee;
    private Unspent unspent;

    public SendDataManager(PaymentService paymentService, DynamicFee dynamicFee, Unspent unspent) {
        this.paymentService = paymentService;
        this.dynamicFee = dynamicFee;
        this.unspent = unspent;
    }

    /**
     * Submits a payment to a specified address and returns the transaction hash if successful
     *
     * @param unspentOutputBundle UTXO object
     * @param keys                A List of elliptic curve keys
     * @param toAddress           The address to send the funds to
     * @param changeAddress       A change address
     * @param bigIntFee           The specified fee amount
     * @param bigIntAmount        The actual transaction amount
     * @return An {@link Observable<String>} where the String is the transaction hash
     */
    public Observable<String> submitPayment(SpendableUnspentOutputs unspentOutputBundle,
                                            List<ECKey> keys,
                                            String toAddress,
                                            String changeAddress,
                                            BigInteger bigIntFee,
                                            BigInteger bigIntAmount) {

        return paymentService.submitPayment(
                unspentOutputBundle,
                keys,
                toAddress,
                changeAddress,
                bigIntFee,
                bigIntAmount)
                .compose(RxUtil.applySchedulersToObservable());
    }

    public Observable<ECKey> getEcKeyFromBip38(String password, String scanData, NetworkParameters networkParameters) {
        return Observable.fromCallable(() -> {
            BIP38PrivateKey bip38 = new BIP38PrivateKey(networkParameters, scanData);
            return bip38.decrypt(password);
        }).compose(RxUtil.applySchedulersToObservable());
    }

    public Observable<SuggestedFee> getSuggestedFee() {
        return Observable.fromCallable(() -> dynamicFee.getDynamicFee())
                .compose(RxUtil.applySchedulersToObservable());
    }

    public Observable<JSONObject> getUnspentOutputs(String address) {
        return Observable.fromCallable(() -> unspent.getUnspentOutputs(address))
                .compose(RxUtil.applySchedulersToObservable());
    }

}
