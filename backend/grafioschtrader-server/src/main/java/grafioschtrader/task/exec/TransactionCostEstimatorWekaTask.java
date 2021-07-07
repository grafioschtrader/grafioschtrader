package grafioschtrader.task.exec;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.dto.ITransactionCost;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import weka.classifiers.Classifier;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.trees.REPTree;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;

/**
 * Train a model to forecast the transaction cost for the for the different
 * trading platform, it does not include other cost like taxes.
 *
 * TODO Integrate it for the estimation of transaction costs
 */
@Component
public class TransactionCostEstimatorWekaTask {
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Transactional
  public void createEstimatorForSecurityaccountsByIdTenant(Integer idTenant) throws Exception {
    List<ITransactionCost> transactionCosts = securityaccountJpaRepository.getAllTransactionCostByTenant(idTenant);
    List<Securityaccount> securtyAccounts = securityaccountJpaRepository.findByIdTenant(idTenant);

    Map<Integer, Securityaccount> securityAccountMap = securtyAccounts.stream()
        .collect(Collectors.toMap(Securityaccount::getIdSecuritycashAccount, s -> s));

    Map<Integer, List<ITransactionCost>> transactionMap = transactionCosts.stream()
        .collect(Collectors.groupingBy(ITransactionCost::getIdSecurityaccount, Collectors.toList()));
    makeModelForSecurityAccount(securityAccountMap, transactionMap);
  }

  public void makeModelForSecurityAccount(Map<Integer, Securityaccount> securityAccountMap,
      Map<Integer, List<ITransactionCost>> transactionMap) throws Exception {

    ArrayList<Attribute> attributes = getAttributeDefinition();
    for (Map.Entry<Integer, List<ITransactionCost>> transactions : transactionMap.entrySet()) {
      Instances instances = this.getInstances(attributes, transactions.getValue(), true);
      Securityaccount securityaccount = securityAccountMap.get(transactions.getKey());
      Classifier trainedModel = trainModel(instances, securityaccount);
      this.saveModelToStore(securityaccount, trainedModel, securityaccountJpaRepository);
    }
  }

  private void saveModelToStore(Securityaccount securityaccount, Classifier classifier,
      SecurityaccountJpaRepository securityaccountJpaRepository) {
    byte[] classifierAsByte = SerializationUtils.serialize((Serializable) classifier);
    securityaccount.setWekaModel(classifierAsByte);
    securityaccountJpaRepository.save(securityaccount);
  }

  private Classifier trainModel(Instances instances, Securityaccount securityaccount) throws Exception {
    instances.setClassIndex(instances.numAttributes() - 1);

    Classifier cModel = null;

    switch (securityaccount.getTradingPlatformPlan().getTransactionFeePlan()) {
    case FP_PER_AMOUNT_GRADUATED:
    case FP_FLAT_OR_PER_AMOUNT_GRADUATED:
      cModel = new REPTree();
      break;
    default:
      cModel = new MultilayerPerceptron();
      ((MultilayerPerceptron) cModel).setOptions(Utils.splitOptions("-L 0.1 -M 0.2 -N 2000 -V 0 -S 0 -E 20 -H a"));
    }

    cModel.buildClassifier(instances);
    return cModel;
  }

  private ArrayList<Attribute> getAttributeDefinition() {
    ArrayList<Attribute> attributes = new ArrayList<>();
    attributes.add(new Attribute("specInvestInstrument"));
    attributes.add(new Attribute("categoryType"));
    attributes.add(new Attribute("getIdStockexchange"));
    attributes.add(new Attribute("price"));
    attributes.add(new Attribute("transactionCost"));

    return attributes;
  }

  private Instances getInstances(ArrayList<Attribute> attributes, List<ITransactionCost> transactionCost,
      boolean forTraining) throws Exception {
    Instances dataSet = new Instances("Rel", attributes, transactionCost.size());
    transactionCost.forEach(tc -> {
      Instance data = new DenseInstance(attributes.size());
      data.setValue(attributes.get(0), tc.getSpecInvestInstrument());
      data.setValue(attributes.get(1), tc.getCategoryType());
      data.setValue(attributes.get(2), tc.getIdStockexchange());
      data.setValue(attributes.get(3), tc.getPrice());
      if (forTraining) {
        data.setValue(attributes.get(4), tc.getTransactionCost());
      }
      dataSet.add(data);
    });
    return dataSet;
  }

  /**
   * Can be used to check a real transaction cost to the estimated.
   *
   * @param securityaccount
   * @throws Exception
   */
  public void checkModel(Securityaccount securityaccount) throws Exception {
    Classifier useModel = this.loadModelFromStore(securityaccount);
    if (useModel != null) {
      ArrayList<Attribute> attributes = getAttributeDefinition();
      List<ITransactionCost> transactionCosts = securityaccountJpaRepository
          .getAllTransactionCostBySecurityaccount(securityaccount.getIdSecuritycashAccount());

      Instances data = getInstances(attributes, transactionCosts, false);
      int i = 0;
      for (Instance instance : data) {
        double[] fDistribution = useModel.distributionForInstance(instance);

        log.info("Security account: {}  Real: {}  forcast: {}", securityaccount.getName(),
            transactionCosts.get(i).getTransactionCost(), fDistribution[0]);
        i++;
      }
    }
  }

  private Classifier loadModelFromStore(Securityaccount securityaccount) {
    return securityaccount.getWekaModel() != null ? SerializationUtils.deserialize(securityaccount.getWekaModel())
        : null;
  }

}
