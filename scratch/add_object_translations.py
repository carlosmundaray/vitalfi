import os
import json
import re

OBJECT_TRANSLATIONS = {
    "es": {
        "title": "Objeto",
        "body": "Proveer un sistema tecnológico inalámbrico capaz de apoyar la detección a través de barreras físicas densas (como concreto u estructuras metálicas) para monitorear parámetros biológicos estimados con la mayor fidelidad posible. Así se buscan optimizar esfuerzos de salvamento y facilitar decisiones oportunas a los rescatistas en el terreno."
    },
    "en": {
        "title": "Objective",
        "body": "Provide a wireless technological system capable of supporting detection through dense physical barriers (such as concrete or metal structures) to monitor estimated biological parameters with the highest possible fidelity, optimizing rescue efforts and facilitating timely decisions for rescuers in the field."
    },
    "zh": {
        "title": "宗旨",
        "body": "提供一种能够在密集物理障碍（如混凝土或金属结构）下辅助生命探测的无线技术系统，以尽可能高的保真度监测估算的生物学参数，从而优化救援行动并帮助一线搜救人员做出及时决策。"
    },
    "ar": {
        "title": "الهدف",
        "body": "توفير نظام تكنولوجي لاسلكي قادر على دعم الكشف عبر الحواجز المادية الكثيفة (مثل الخرسانة أو الهياكل المعدنية) لرصد المؤشرات الحيوية المقدرة بأعلى دقة ممكنة، مما يحسن جهود الإنقاذ ويسهل اتخاذ قرارات سريعة لفرق الإنقاذ الميدانية."
    },
    "pt": {
        "title": "Objetivo",
        "body": "Fornecer um sistema tecnológico sem fio capaz de apoiar a detecção através de barreiras físicas densas (como concreto ou estruturas metálicas) para monitorar parâmetros biológicos estimados com a maior fidelidade possível, otimizando esforços de salvamento e facilitando decisões oportunas para os socorristas em campo."
    },
    "fr": {
        "title": "Objectif",
        "body": "Fournir un système technologique sans fil capable d'appuyer la détection à travers des barrières physiques denses (comme le béton ou les structures métalliques) afin de surveiller les paramètres biologiques estimés avec la plus grande fidélité possible, optimisant ainsi les efforts de sauvetage et facilitant les décisions des secouristes sur le terrain."
    },
    "de": {
        "title": "Zweck",
        "body": "Bereitstellung eines drahtlosen technologischen Systems zur Erkennung durch dichte physische Barrieren (wie Beton oder Metallstrukturen), um geschätzte biologische Parameter mit höchster Genauigkeit zu überwachen, Rettungseinsätze zu optimieren und Helfern vor Ort zeitnahe Entscheidungen zu erleichtern."
    },
    "it": {
        "title": "Obiettivo",
        "body": "Fornire un sistema tecnologico wireless in grado di supportare il rilevamento attraverso barriere fisiche dense (come cemento o strutture metalliche) per monitorare i parametri biologici stimati con la massima fedeltà possibile, ottimizzando i soccorsi e facilitando decisioni tempestive sul campo."
    },
    "ru": {
        "title": "Цель",
        "body": "Предоставить беспроводную технологическую систему для обнаружения сквозь плотные физические преграды (бетон или металлические конструкции) с целью мониторинга расчетных биологических параметров с максимальной точностью, оптимизируя спасательные работы и ускоряя принятие решений спасателями на месте."
    },
    "ja": {
        "title": "目的",
        "body": "コンクリートや金属構造などの高密度な物理的障害物を透過して生命探知を支援し、推定生体パラメータを高精度で監視することで、救助活動を最適化し現場の救助隊員の迅速な意思決定を支援するワイヤレス技術システムを提供します。"
    },
    "hi": {
        "title": "उद्देश्य",
        "body": "ठोस भौतिक बाधाओं (जैसे कंक्रीट या धातु संरचनाओं) के माध्यम से खोज में सहायता करने और अनुमानित जैविक मापदंडों की निगरानी करने के लिए एक वायरलेस तकनीकी प्रणाली प्रदान करना, जिससे बचाव प्रयासों को अनुकूलित किया जा सके और समय पर निर्णय लिए जा सकें।"
    },
    "id": {
        "title": "Tujuan",
        "body": "Menyediakan sistem teknologi nirkabel yang mampu mendukung deteksi melalui penghalang fisik padat (seperti beton atau struktur logam) untuk memantau perkiraan parameter biologis dengan akurasi setinggi mungkin, mengoptimalkan upaya penyelamatan dan memfasilitasi keputusan cepat bagi tim penyelamat."
    },
    "uk": {
        "title": "Мета",
        "body": "Надати бездротову технологічну систему для виявлення крізь щільні фізичні перешкоди (бетон або металеві конструкції) з метою моніторингу біологічних параметрів із максимальною точністю, оптимізуючи рятувальні роботи та прискорюючи прийняття рішень на місці подій."
    },
    "bn": {
        "title": "উদ্দেশ্য",
        "body": "ঘন কাঠামোগত বাধার (যেমন কংক্রিট বা ধাতু) মধ্য দিয়ে সন্ধানে সহায়তা করতে এবং আনুমানিক জৈবিক পরামিতিগুলি নির্ভুলভাবে পর্যবেক্ষণ করতে একটি ওয়্যারলেস প্রযুক্তিগত ব্যবস্থা সরবরাহ করা, যা উদ্ধার তৎপরতাকে ত্বরান্বিত করবে।"
    },
    "ko": {
        "title": "목적",
        "body": "콘크리트나 금속 구조물과 같은 밀집된 물리적 장벽을 통과하여 탐지를 지원하고 추정된 생체 신호를 정밀하게 모니터링하여 구조 작업을 최적화하고 현장 구조대원의 신속한 결정을 돕는 무선 기술 시스템을 제공합니다."
    },
    "vi": {
        "title": "Mục tiêu",
        "body": "Cung cấp hệ thống công nghệ không dây hỗ trợ phát hiện xuyên qua các chướng ngại vật vật lý dày đặc (như bê tông hoặc kết cấu kim loại) để theo dõi các thông số sinh học ước tính với độ chính xác cao nhất, tối ưu hóa công tác cứu hộ và hỗ trợ quyết định kịp thời cho lực lượng cứu hộ tại hiện trường."
    },
    "pl": {
        "title": "Cel",
        "body": "Zapewnienie bezprzewodowego systemu technologicznego wspierającego detekcję przez gęste bariery fizyczne (np. beton lub konstrukcje metalowe) w celu monitorowania szacowanych parametrów biologicznych z najwyższą możliwą wiernością, optymalizując działania ratownicze i ułatwiając podejmowanie decyzji ratownikom w terenie."
    },
    "nl": {
        "title": "Doel",
        "body": "Het leveren van een draadloos technologisch systeem ter ondersteuning van detectie door dichte fysieke barrières (zoals beton of metalen constructies) om geschatte biologische parameters zo nauwkeurig mogelijk te monitoren, waardoor reddingsinspanningen worden geoptimaliseerd en tijdige beslissingen door hulpverleners worden vergemakkelijkt."
    },
    "fa": {
        "title": "هدف",
        "body": "ارائه یک سامانه فناورانه بی‌سیم با قابلیت پشتیبانی از تشخیص در موانع فیزیکی متراکم (مانند بتن یا سازه‌های فلزی) جهت پایش پارامترهای زیستی تخمینی با بالاترین دقت، بهینه‌سازی عملیات نجات و تسهیل تصمیم‌گیری‌های به‌موقع برای امدادگران در میدان."
    },
    "th": {
        "title": "วัตถุประสงค์",
        "body": "จัดหาระบบเทคโนโลยีไร้สายที่สามารถรองรับการตรวจจับผ่านสิ่งกีดขวางทางกายภาพที่หนาแน่น (เช่น คอนกรีตหรือโครงสร้างโลหะ) เพื่อตรวจสอบพารามิเตอร์ทางชีวภาพโดยประมาณด้วยความแม่นยำสูงสุด เพิ่มประสิทธิภาพในการกู้ภัยและช่วยให้ผู้ช่วยเหลือในพื้นที่ตัดสินใจได้ทันท่วงที"
    },
    "sw": {
        "title": "Madhumuni",
        "body": "Kutoa mfumo wa kiteknolojia usiotumia waya wenye uwezo wa kusaidia ugunduzi kupitia vizuizi vizito vya kimwili (kama zege au miundo ya chuma) kufuatilia makadirio ya vigezo vya kibiolojia kwa usahihi wa juu, kuboresha juhudi za uokoaji na kuwezesha maamuzi ya haraka kwa waokoaji uwandani."
    },
    "ro": {
        "title": "Obiectiv",
        "body": "Furnizarea unui sistem tehnologic fără fir capabil să sprijine detecția prin bariere fizice dense (cum ar fi betonul sau structurile metalice) pentru a monitoriza parametrii biologici estimați cu cea mai mare fidelitate posibilă, optimizând eforturile de salvare și facilitând deciziile salvatorilor pe teren."
    },
    "el": {
        "title": "Σκοπός",
        "body": "Παροχή ενός ασύρματου τεχνολογικού συστήματος ικανού να υποστηρίξει τον εντοπισμό μέσα από πυκνά φυσικά εμπόδια (όπως σκυρόδεμα ή μεταλλικές κατασκευές) για την παρακολούθηση εκτιμώμενων βιολογικών παραμέτρων με τη μέγιστη δυνατή ακρίβεια, βελτιστοποιώντας τις προσπάθειες διάσωσης και διευκολύνοντας τις έγκαιρες αποφάσεις των διασωστών στο πεδίο."
    },
    "cs": {
        "title": "Účel",
        "body": "Poskytnutí bezdrátového technologického systému schopného podpořit detekci přes husté fyzické překážky (jako beton nebo kovové konstrukce) za účelem sledování odhadovaných biologických parametrů s nejvyšší možnou přesností, optimalizace záchranných prací a usnadnění včasných rozhodnutí záchranářů v terénu."
    },
    "sv": {
        "title": "Syfte",
        "body": "Tillhandahålla ett trådlöst tekniskt system som kan stödja detektering genom täta fysiska barriärer (såsom betong eller metallstrukturer) för att övervaka uppskattade biologiska parametrar med högsta möjliga tillförlitlighet, vilket optimerar räddningsinsatser och underlättar snabba beslut för räddningspersonal på fältet."
    },
    "tl": {
        "title": "Layunin",
        "body": "Magbigay ng wireless na sistemang teknolohikal na may kakayahang sumuporta sa pagtukoy sa pamamagitan ng siksik na mga pisikal na harang (tulad ng kongkreto o mga istrukturang metal) upang subaybayan ang tinatayang mga biyolohikal na parameter nang may pinakamataas na katumpakan, pagpapabuti ng mga operasyon ng pagsagip at pagpapadali ng napapanahong mga desisyon para sa mga rescuer sa field."
    },
    "he": {
        "title": "מטרה",
        "body": "אספקת מערכת טכנולוגית אלחוטית המסוגלת לסייע באיתור דרך מחסומים פיזיים צפופים (כגון בטון או מבני מתכת) לניטור מדדים ביולוגיים משוערים בדיוק המרבי האפשרי, לייעול מאמצי החילוץ ולהקלת קבלת החלטות מהירה עבור צוותי החילוץ בשטח."
    },
    "tr": {
        "title": "Amaç",
        "body": "Yoğun fiziksel engeller (beton veya metal yapılar gibi) arasından tespit yapabilen kablosuz bir teknolojik sistem sağlayarak tahmin edilen biyolojik parametreleri mümkün olan en yüksek doğrulukla izlemek, kurtarma çalışmalarını optimize etmek ve sahadaki kurtarma ekiplerinin zamanında karar almasını kolaylaştırmak."
    }
}

# 1. Update HTML files (landing/index.html & index.html)
for hpath in [r"c:\xampp\htdocs\vitalfi\landing\index.html", r"c:\xampp\htdocs\vitalfi\index.html"]:
    if not os.path.exists(hpath):
        continue
    with open(hpath, "r", encoding="utf-8") as f:
        html = f.read()
    
    html = html.replace(
        '<h2 data-od-id="object.title">Objeto</h2>',
        '<h2 data-od-id="object.title" data-i18n="object.title">Objeto</h2>'
    )
    html = html.replace(
        '<p data-od-id="object.body">',
        '<p data-od-id="object.body" data-i18n="object.body">'
    )
    with open(hpath, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Updated data-i18n tags in {hpath}")

# 2. Update i18n.js files (landing/i18n.js & i18n.js)
for jpath in [r"c:\xampp\htdocs\vitalfi\landing\i18n.js", r"c:\xampp\htdocs\vitalfi\i18n.js"]:
    if not os.path.exists(jpath):
        continue
    with open(jpath, "r", encoding="utf-8") as f:
        js = f.read()

    # In each language block, after "vision.body": "...", insert "object.title" and "object.body"
    for lang, texts in OBJECT_TRANSLATIONS.items():
        title_escaped = json.dumps(texts["title"], ensure_ascii=False)
        body_escaped = json.dumps(texts["body"], ensure_ascii=False)
        
        # Pattern to find vision.body for this language or generally
        # We can find `"${lang}": {` then find `"vision.body": ...`
        pattern = rf'("{lang}"\s*:\s*\{{[\s\S]*?"vision\.body"\s*:\s*".*?",)'
        match = re.search(pattern, js)
        if match:
            matched_str = match.group(1)
            # check if object.title already there
            if f'"object.title"' not in matched_str:
                replacement = f'{matched_str}\n    "object.title": {title_escaped},\n    "object.body": {body_escaped},'
                js = js.replace(matched_str, replacement)
    
    with open(jpath, "w", encoding="utf-8") as f:
        f.write(js)
    print(f"Updated translations in {jpath}")

print("Added object.title and object.body translations across all languages!")
