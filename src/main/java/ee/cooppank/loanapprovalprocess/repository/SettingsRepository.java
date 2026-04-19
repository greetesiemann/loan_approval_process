package ee.cooppank.loanapprovalprocess.repository;

import ee.cooppank.loanapprovalprocess.entity.Settings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettingsRepository extends JpaRepository<Settings, String> {
}
