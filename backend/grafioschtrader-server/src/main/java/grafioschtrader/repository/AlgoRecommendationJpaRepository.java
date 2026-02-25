package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.AlgoRecommendation;

public interface AlgoRecommendationJpaRepository extends JpaRepository<AlgoRecommendation, Integer> {
}
